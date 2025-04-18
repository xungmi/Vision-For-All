from flask import Flask
import os

app = Flask(__name__)
counter_file = "/data/counter.txt"


@app.route("/")
def home():
    # Nếu file không tồn tại, tạo và khởi tạo bằng 0
    if not os.path.exists(counter_file):
        with open(counter_file, "w") as f:
            f.write("0")

    # Đọc số lượt truy cập hiện tại
    with open(counter_file, "r") as f:
        count = int(f.read())

    # Tăng lên 1
    count += 1

    # Ghi lại vào file
    with open(counter_file, "w") as f:
        f.write(str(count))

    return f"Số lượt truy cập: {count}\n"


if __name__ == "__main__":
    app.run(host="0.0.0.0", port=5000)
