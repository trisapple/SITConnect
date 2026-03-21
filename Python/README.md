# C2 Server Web Dashboard

A modern Flask-based web interface for the C2 (Command and Control) Server with real-time communication using SocketIO.

## Features

- 🎯 **Real-time Client Management**: View all connected clients in real-time
- 💻 **Interactive Command Console**: Send commands and receive responses through a clean web interface
- 📥 **File Download Support**: Download files from remote clients directly through the web interface
- 🔄 **Live Updates**: Automatic updates when clients connect or disconnect
- 🎨 **Modern UI**: Dark-themed, responsive dashboard with intuitive controls

## Architecture

- **Backend**: Flask + Flask-SocketIO running on port 5000
- **C2 Socket Server**: Running on port 5001 (handles client connections)
- **Frontend**: Vanilla JavaScript with Socket.IO client for real-time updates

## Installation

1. **Install Python dependencies**:
   ```bash
   pip install -r requirements.txt
   ```

   Or install manually:
   ```bash
   pip install Flask==3.0.0 flask-socketio==5.3.5 python-socketio==5.10.0
   ```

## Usage

1. **Start the Flask web server**:
   ```bash
   python3 flask_app.py
   ```

   This will:
   - Start the C2 socket server on port 5001
   - Start the Flask web interface on port 5000

2. **Access the web dashboard**:
   Open your browser and navigate to:
   ```
   http://localhost:5000
   ```

3. **Connect clients**:
   - Your C2 clients should connect to port 5001 (the socket server)
   - Connected clients will automatically appear in the dashboard

4. **Send commands**:
   - Click on a client in the left panel to select it
   - Type commands in the input box at the bottom
   - Press Enter or click "Send" to execute
   - View responses in real-time in the console

## Quick Commands

The dashboard includes quick command buttons for common operations:
- `ls` - List directory contents
- `pwd` - Print working directory
- `whoami` - Show current user
- `ps` - Show running processes
- `download` - Download files from client

## File Downloads

To download a file from a client:
```bash
download /path/to/file.txt
```

The file will be saved to the `downloads/` directory and a download link will appear in the console.

## Project Structure

```
Python/
├── flask_app.py           # Main Flask application
├── app.py                 # Original C2 socket server
├── requirements.txt       # Python dependencies
├── templates/
│   └── index.html        # Web dashboard HTML
├── static/
│   ├── css/
│   │   └── style.css     # Dashboard styling
│   └── js/
│       └── app.js        # Frontend JavaScript
└── downloads/            # Downloaded files (created automatically)
```

## Technical Details

### Backend (flask_app.py)
- Runs C2 socket server in a background thread
- Manages client connections and state
- Handles command execution and file transfers
- Provides REST API and WebSocket endpoints

### Frontend
- Real-time updates via Socket.IO
- Clean, terminal-style interface
- Responsive design for desktop and mobile
- Command history and error handling

## Security Notes

⚠️ **This is for educational purposes only**

- Change the SECRET_KEY in production
- Use proper authentication for web access
- Implement SSL/TLS for secure connections
- Be aware of legal implications when deploying C2 infrastructure

## Troubleshooting

**Port already in use**:
```bash
# Find and kill process on port 5000 or 5001
lsof -ti:5000 | xargs kill -9
lsof -ti:5001 | xargs kill -9
```

**Dependencies not installing**:
```bash
# Upgrade pip first
pip install --upgrade pip
# Then install requirements
pip install -r requirements.txt
```

**Client not connecting**:
- Ensure firewall allows connections on port 5001
- Check if server is listening: `netstat -an | grep 5001`
- Verify client is using correct IP address

## Contributing

Feel free to enhance the dashboard with additional features like:
- Client authentication
- Command history
- File upload to clients
- Multiple simultaneous client command execution
- Session persistence

## License

Educational use only. Use responsibly and legally.
