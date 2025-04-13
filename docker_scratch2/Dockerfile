ARG PYTHON_VERSION=3.10

FROM python:${PYTHON_VERSION}-slim

# Đặt biến môi trường
ENV GREETING="Hello from ENV"
ENV FLASK_PORT=6000
ENV QUOTE_FILE="quote.txt"

RUN apt-get update && apt-get install -y curl

WORKDIR /app

COPY requirements.txt .


# Tải một file text từ internet và lưu thành quote.txt
ADD https://www.w3.org/TR/PNG/iso_8859-1.txt quote.txt

# Nếu ko đổi => dùng cache
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

VOLUME ["/app/data"]

EXPOSE 6000

CMD ["python", "app.py"]
