FROM python:3.11-slim

# Evita que Python genere los archivos .pyc
ENV PYTHONDONTWRITEBYTECODE=1

# Fuerza a que los logs salgan a la consola sin retraso
ENV PYTHONUNBUFFERED=1

RUN apt-get update && apt-get install -y \
    default-libmysqlclient-dev \
    gcc \
    pkg-config \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

COPY . .

RUN python manage.py collectstatic --noinput 2>/dev/null || true

EXPOSE 8002

CMD ["python", "manage.py", "runserver", "0.0.0.0:8002"]