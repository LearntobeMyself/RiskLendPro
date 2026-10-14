"""Upload seed catalog images to Tencent COS, then optionally write product rows.

Runtime URLs are never local files: the app stores object_key and CosStorageService.accessUrl
returns a signed GET link from the bucket.

Usage (from repo root):
  python docs/seed-catalog/sync_to_cos.py
  python docs/seed-catalog/sync_to_cos.py --with-sql
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
IMAGE_DIR = Path(__file__).resolve().parent / "images"
LOCAL_YML = ROOT / "config" / "local" / "application-local.yml"
COMMON_YML = ROOT / "services" / "common-runtime" / "src" / "main" / "resources" / "application-common.yml"
SQL_FILE = ROOT / "sql" / "seed_loan_catalog_demo.sql"
MIGRATION_FILE = ROOT / "sql" / "migration_loan_catalog.sql"

OBJECTS = [
    ("logo-xinghe-bank.jpg", "loan/institution_logo/seed/logo-xinghe-bank.jpg"),
    ("logo-lanhai-cf.jpg", "loan/institution_logo/seed/logo-lanhai-cf.jpg"),
    ("logo-qingwu-micro.jpg", "loan/institution_logo/seed/logo-qingwu-micro.jpg"),
    ("logo-tuan-auto.jpg", "loan/institution_logo/seed/logo-tuan-auto.jpg"),
    ("cover-salary-flash.jpg", "loan/cover/seed/cover-salary-flash.jpg"),
    ("cover-revolving.jpg", "loan/cover/seed/cover-revolving.jpg"),
    ("cover-housing-fund.jpg", "loan/cover/seed/cover-housing-fund.jpg"),
    ("cover-civil-servant.jpg", "loan/cover/seed/cover-civil-servant.jpg"),
    ("cover-sme-working.jpg", "loan/cover/seed/cover-sme-working.jpg"),
    ("cover-self-employed.jpg", "loan/cover/seed/cover-self-employed.jpg"),
    ("cover-home-mortgage.jpg", "loan/cover/seed/cover-home-mortgage.jpg"),
    ("cover-car-mortgage.jpg", "loan/cover/seed/cover-car-mortgage.jpg"),
    ("cover-decoration.jpg", "loan/cover/seed/cover-decoration.jpg"),
    ("cover-education.jpg", "loan/cover/seed/cover-education.jpg"),
    ("cover-digital.jpg", "loan/cover/seed/cover-digital.jpg"),
    ("cover-summer-off.jpg", "loan/cover/seed/cover-summer-off.jpg"),
]


def _section(path: Path, name: str) -> dict[str, str]:
    if not path.is_file():
        return {}
    values: dict[str, str] = {}
    in_section = False
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.split("#", 1)[0].rstrip()
        if not line.strip():
            continue
        if re.match(r"^[A-Za-z0-9_.-]+:\s*$", line):
            in_section = line.strip() == f"{name}:"
            continue
        if in_section and ":" in line:
            key, value = line.strip().split(":", 1)
            values[key.strip()] = value.strip().strip("\"'")
    return values


def _env_or(value: str, default: str) -> str:
    match = re.fullmatch(r"\$\{([A-Z0-9_]+):?(.*)\}", value)
    if not match:
        return value
    import os

    return os.environ.get(match.group(1), match.group(2) if match.group(2) != "" else default)


def storage_config() -> dict[str, str]:
    local = _section(LOCAL_YML, "storage")
    common = _section(COMMON_YML, "storage")
    merged = {**common, **local}
    bucket = _env_or(merged.get("bucket", ""), "")
    region = _env_or(merged.get("region", "ap-guangzhou"), "ap-guangzhou")
    secret_id = _env_or(merged.get("secret-id", ""), "")
    secret_key = _env_or(merged.get("secret-key", ""), "")
    if not bucket or not secret_id or not secret_key:
        raise SystemExit("COS 未配置：请在 config/local/application-local.yml 填写 bucket / secret-id / secret-key")
    return {"bucket": bucket, "region": region, "secret_id": secret_id, "secret_key": secret_key}


def _placeholder(text: str, key: str, fallback: str) -> str:
    match = re.search(rf"\$\{{{re.escape(key)}:([^}}]*)\}}", text)
    raw = match.group(1) if match else fallback
    return _env_or(f"${{{key}:{raw}}}", fallback)


def db_config() -> tuple[str, int, str, str, str]:
    text = COMMON_YML.read_text(encoding="utf-8")
    if LOCAL_YML.is_file():
        text = LOCAL_YML.read_text(encoding="utf-8") + "\n" + text
    jdbc = _placeholder(text, "PRIMARY_DB_URL", "")
    user = _placeholder(text, "PRIMARY_DB_USERNAME", "admin")
    password = _placeholder(text, "PRIMARY_DB_PASSWORD", "admin")
    parsed = re.search(r"jdbc:mysql://([^:/]+):(\d+)/([^?]+)", jdbc)
    if not parsed:
        raise SystemExit("无法解析主库 jdbc-url")
    return parsed.group(1), int(parsed.group(2)), parsed.group(3), user, password


def upload(client, bucket: str) -> None:
    missing = [name for name, _ in OBJECTS if not (IMAGE_DIR / name).is_file()]
    if missing:
        raise SystemExit("缺少本地图片: " + ", ".join(missing))
    for name, key in OBJECTS:
        path = IMAGE_DIR / name
        size = path.stat().st_size
        client.put_object(
            Bucket=bucket,
            Key=key,
            Body=path.read_bytes(),
            ContentType="image/jpeg",
        )
        head = client.head_object(Bucket=bucket, Key=key)
        remote_size = int(head.get("Content-Length", 0))
        if remote_size != size:
            raise SystemExit(f"尺寸不一致 {key}: local={size} remote={remote_size}")
        print(f"OK  {key}  ({size} bytes)")


def mysql_cli():
    host, port, database, user, password = db_config()
    mysql = Path(r"D:\mysql-8.0.43-winx64\bin\mysql.exe")
    if not mysql.is_file():
        raise SystemExit("找不到 mysql.exe")
    import os
    import subprocess

    env = os.environ.copy()
    env["MYSQL_PWD"] = password
    return mysql, host, port, database, user, env, subprocess


def run_sql_file(path: Path | None, extra: str | None = None) -> str:
    mysql, host, port, database, user, env, subprocess = mysql_cli()
    payload = path.read_text(encoding="utf-8") if path is not None else ""
    if extra:
        payload = payload + "\n" + extra
    completed = subprocess.run(
        [
            str(mysql),
            "-h", host,
            "-P", str(port),
            "-u", user,
            "--default-character-set=utf8mb4",
            database,
        ],
        input=payload,
        capture_output=True,
        text=True,
        env=env,
        encoding="utf-8",
    )
    if completed.returncode != 0:
        detail = (completed.stderr or completed.stdout or "mysql 执行失败").strip()
        raise SystemExit(detail)
    return (completed.stdout or "").strip()


def run_sql() -> None:
    _, host, port, database, user, _, _ = mysql_cli()
    print(f"连接业务库 {host}:{port}/{database} 用户 {user}")
    print("建目录表…")
    run_sql_file(MIGRATION_FILE)
    print(f"OK  {MIGRATION_FILE.as_posix()}")
    print("写入演示产品…")
    run_sql_file(SQL_FILE)
    print(f"OK  {SQL_FILE.as_posix()}")
    counts = run_sql_file(
        None,
        extra="""
SELECT 'institution' AS k, COUNT(*) AS n FROM loan_institution WHERE deleted=0
UNION ALL SELECT 'product', COUNT(*) FROM loan_product WHERE deleted=0
UNION ALL SELECT 'on_shelf', COUNT(*) FROM loan_product WHERE deleted=0 AND status='ON_SHELF'
UNION ALL SELECT 'image', COUNT(*) FROM loan_image WHERE status='BOUND';
""",
    )
    print(counts)


def split_sql(sql: str) -> list[str]:
    statements: list[str] = []
    buf: list[str] = []
    for raw in sql.splitlines():
        line = raw.strip()
        if not line or line.startswith("--"):
            continue
        buf.append(raw)
        if raw.rstrip().endswith(";"):
            text = "\n".join(buf).strip().rstrip(";").strip()
            if text:
                statements.append(text)
            buf = []
    if buf:
        text = "\n".join(buf).strip().rstrip(";").strip()
        if text:
            statements.append(text)
    return statements


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--with-sql", action="store_true", help="上传完成后执行 seed_loan_catalog_demo.sql")
    parser.add_argument("--sql-only", action="store_true", help="跳过上传，建表并写入演示产品")
    args = parser.parse_args()
    if args.sql_only:
        run_sql()
        return 0
    try:
        from qcloud_cos import CosConfig, CosS3Client
    except ImportError:
        print("缺少 cos-python-sdk-v5，正在安装…", file=sys.stderr)
        import subprocess

        subprocess.check_call([sys.executable, "-m", "pip", "install", "cos-python-sdk-v5", "pymysql"])
        from qcloud_cos import CosConfig, CosS3Client
    cfg = storage_config()
    client = CosS3Client(CosConfig(Region=cfg["region"], SecretId=cfg["secret_id"], SecretKey=cfg["secret_key"]))
    print(f"上传到桶 {cfg['bucket']} / {cfg['region']}，共 {len(OBJECTS)} 个对象")
    upload(client, cfg["bucket"])
    if args.with_sql:
        run_sql()
    print("完成。产品接口里的 url 由 CosStorageService.accessUrl(objectKey) 签发，不读本地文件。")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
