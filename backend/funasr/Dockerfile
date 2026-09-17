FROM python:3.11-slim-bookworm

WORKDIR /app
ENV TZ=Asia/Shanghai \
    PYTHONUNBUFFERED=1 \
    MODELSCOPE_CACHE=/models \
    HF_HOME=/models/hf \
    FUNASR_BIND=127.0.0.1 \
    FUNASR_PORT=10095 \
    FUNASR_DEVICE=cpu \
    FUNASR_ONLINE_MODEL=paraformer-zh-streaming \
    FUNASR_OFFLINE_MODEL=paraformer-zh \
    PIP_INDEX_URL=https://mirrors.aliyun.com/pypi/simple \
    PIP_EXTRA_INDEX_URL=https://download.pytorch.org/whl/cpu

RUN sed -i 's|deb.debian.org|mirrors.aliyun.com|g; s|security.debian.org|mirrors.aliyun.com|g' /etc/apt/sources.list.d/debian.sources \
    && apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY server.py .
EXPOSE 10095
VOLUME ["/models"]
CMD ["python", "-u", "server.py"]
