from flask import Flask
import os

app = Flask(__name__)


@app.route("/")
def hello():
    greeting = os.getenv("GREETING", "Default Greeting")
    quote_file = os.getenv("QUOTE_FILE", "quote.txt")
    try:
        with open(quote_file, "r") as f:
            lines = f.readlines()
            content = "".join(lines[:2])  # Lấy 2 dòng đầu tiên
    except FileNotFoundError:
        content = "File not found."
    return f"""
        <h1>{greeting}</h1>
        <h2>Hello from Docker!</h2>
        <pre>{content}</pre>
    """


if __name__ == "__main__":
    port = int(os.getenv("FLASK_PORT", 6000))
    app.run(host="0.0.0.0", port=port)
