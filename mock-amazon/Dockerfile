FROM python:3.10-slim

WORKDIR /app
COPY . /app/

RUN pip install flask requests

ENV FLASK_APP=mock-amazon.py
EXPOSE 8080

CMD ["python", "mock-amazon.py"]
