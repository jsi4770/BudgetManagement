# 영수증 OCR 서버(Django) 이미지.
#
#   docker compose up -d --build ocr   # backend/compose.yaml 에서
#
# README 의 로컬 실행(`python manage.py runserver 8000`)과 같은 개발 서버로 띄웁니다 —
# 로컬·시연용입니다. 운영에 올린다면 gunicorn 같은 WSGI 서버로 바꾸세요.

FROM python:3.14-slim

# 로그가 버퍼에 갇히지 않게(docker logs 로 바로 보이게), .pyc 를 만들지 않게
ENV PYTHONUNBUFFERED=1 \
    PYTHONDONTWRITEBYTECODE=1 \
    TZ=Asia/Seoul

WORKDIR /app

# 의존성을 먼저 설치해 소스만 바뀐 재빌드는 이 레이어를 재사용합니다.
COPY requirements.txt ./
RUN pip install --no-cache-dir -r requirements.txt

# /app 폴더 자체도 ocr 소유로 — COPY --chown 은 복사한 파일만 바꾸고 WORKDIR 은 root 소유로
# 남아서, 아래 migrate 가 새 파일(db.sqlite3)을 만들지 못합니다.
RUN useradd --system --no-create-home ocr && chown ocr:ocr /app

COPY --chown=ocr:ocr . .
USER ocr

# runserver 는 기동할 때 SQLite 를 열어 마이그레이션 상태를 확인하는데, 로컬의 db.sqlite3 는
# 이미지에 넣지 않으므로(.dockerignore) 여기서 빈 DB 를 만듭니다. 이게 없으면
# "unable to open database file" 로 기동하자마자 죽습니다. 이 서버가 저장하는 데이터는 없습니다.
RUN python manage.py migrate --noinput

EXPOSE 8000

# 0.0.0.0 — 컨테이너 밖(호스트·was 컨테이너)에서 붙을 수 있게.
# --noreload — 코드가 이미지에 고정돼 있어 자동 재시작이 필요 없고, 자식 프로세스 없이
# python 이 PID 1 이 되어 docker stop 신호를 직접 받습니다.
CMD ["python", "manage.py", "runserver", "0.0.0.0:8000", "--noreload"]
