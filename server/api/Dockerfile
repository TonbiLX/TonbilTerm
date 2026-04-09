# ===========================================================================
# Tonbil API - Multi-stage Dockerfile
# ===========================================================================

# --- Base stage: dependency'ler ---
FROM python:3.12-slim AS base

WORKDIR /app

# Sistem bagimliliklari (asyncpg icin libpq gerekli)
RUN apt-get update && \
    apt-get install -y --no-install-recommends \
        libpq-dev \
        curl \
    && rm -rf /var/lib/apt/lists/*

COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# --- Dev stage: hot reload icin ---
FROM base AS dev

# Watchfiles (uvicorn reload icin)
RUN pip install --no-cache-dir watchfiles

COPY . .

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--reload"]

# --- Production stage ---
FROM base AS production

# Non-root kullanici
RUN groupadd -r tonbil && useradd -r -g tonbil -d /app tonbil

COPY . .

RUN chown -R tonbil:tonbil /app

USER tonbil

EXPOSE 8000

HEALTHCHECK --interval=15s --timeout=5s --retries=3 --start-period=20s \
    CMD python -c "import urllib.request; urllib.request.urlopen('http://localhost:8000/health')"

CMD ["uvicorn", "app.main:app", "--host", "0.0.0.0", "--port", "8000", "--workers", "2"]
