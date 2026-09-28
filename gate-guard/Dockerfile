FROM python:3.12-slim

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY gate_guard/ ./gate_guard/

ENV PYTHONUNBUFFERED=1
VOLUME ["/app/logs", "/app/config"]

ENTRYPOINT ["python", "-m", "gate_guard", "--config", "/app/config/config.yaml"]
CMD ["run"]
