FROM python:3.11-slim

WORKDIR /app

# Install system dependencies
RUN apt-get update && apt-get install -y \
    postgresql-client \
    && rm -rf /var/lib/apt/lists/*

# Copy requirements and install Python dependencies
COPY requirements.txt .
RUN pip install --no-cache-dir -r requirements.txt

# Copy source code
COPY . .

# Create storage directory
RUN mkdir -p /app/storage

# Expose port
EXPOSE 8000

# Environment variables
ENV PYTHONPATH=/app
ENV DATABASE_URL=postgresql://filesync:filesync_password@postgres:5432/filesync
ENV REDIS_URL=redis://redis:6379
ENV RABBITMQ_URL=amqp://filesync:filesync_password@rabbitmq:5672/

# Run the application
CMD ["python", "server/main.py"]
