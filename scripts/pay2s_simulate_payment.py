#!/usr/bin/env python3
"""Simulate an existing Pay2S demo order; credentials stay in the local process."""

import argparse
import json
import os
from pathlib import Path
import re
import sys
from urllib import error, request
import uuid


ENDPOINT = "https://payment.pay2s.vn/v1/gateway/api/simulate-payment"
ENV_FILE = Path(__file__).resolve().parents[1] / ".env"


class NoRedirect(request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        # Never forward the Authorization header to another endpoint.
        return None


def configuration():
    values = {}
    if ENV_FILE.exists():
        for line in ENV_FILE.read_text(encoding="utf-8-sig").splitlines():
            key, separator, value = line.strip().removeprefix("export ").partition("=")
            key = key.strip()
            if separator and key in {"PAYMENTS_ENVIRONMENT", "PAY2S_SECRET_KEY"}:
                value = value.strip()
                if len(value) >= 2 and value[0] == value[-1] and value[0] in "\"'":
                    value = value[1:-1]
                values[key] = value
    for key in ("PAYMENTS_ENVIRONMENT", "PAY2S_SECRET_KEY"):
        if key in os.environ:
            values[key] = os.environ[key]
    return values


def main():
    parser = argparse.ArgumentParser(
        description="Mô phỏng một đơn demo Pay2S, đọc khóa từ sam-be/.env."
    )
    parser.add_argument("order_id", nargs="?", help="orderId UUID từ response checkout SAM")
    parser.add_argument(
        "--resend-ipn", action="store_true", help="Yêu cầu Pay2S gửi lại IPN của đơn"
    )
    args = parser.parse_args()
    config = configuration()
    if config.get("PAYMENTS_ENVIRONMENT", "").strip().upper() != "SANDBOX":
        sys.exit("Từ chối: PAYMENTS_ENVIRONMENT phải là SANDBOX.")
    secret = config.get("PAY2S_SECRET_KEY", "").strip()
    if not secret or re.search(r"\s", secret) or "${" in secret:
        sys.exit("PAY2S_SECRET_KEY thiếu hoặc không hợp lệ; kiểm tra cấu hình local.")

    order_id = args.order_id or input("Dán orderId của đơn cần mô phỏng: ")
    order_id = order_id.strip()
    if not re.fullmatch(r"[0-9a-fA-F]{32}|[0-9a-fA-F]{8}(?:-[0-9a-fA-F]{4}){3}-[0-9a-fA-F]{12}", order_id):
        sys.exit("orderId phải là UUID của SAM, không phải mã PAY2SJSC hoặc link QR.")
    order_id = uuid.UUID(order_id).hex
    body = json.dumps({"orderId": order_id, "resendIpn": args.resend_ipn}).encode()
    req = request.Request(
        ENDPOINT,
        data=body,
        headers={
            "Authorization": "Bearer " + secret,
            "Content-Type": "application/json",
            "Accept": "application/json",
        },
        method="POST",
    )
    print("Đang mô phỏng đơn demo:", order_id, flush=True)
    try:
        with request.build_opener(NoRedirect()).open(req, timeout=20) as response:
            raw = response.read(65537)
        if len(raw) > 65536:
            sys.exit("Phản hồi Pay2S vượt giới hạn; không in nội dung để bảo vệ thông tin.")
        result = json.loads(raw)
    except error.HTTPError as exc:
        explanations = {
            401: "Secret Key không hợp lệ.",
            403: "Tài khoản hoặc đơn không phải demo.",
            404: "Không tìm thấy đơn trong phạm vi Secret Key đang dùng.",
            409: "Đơn không đủ điều kiện; kiểm tra trạng thái, ngân hàng demo và IPN URL.",
        }
        sys.exit(f"Pay2S HTTP {exc.code}: " + explanations.get(exc.code, "Kiểm tra cấu hình/đơn trên Pay2S."))
    except (error.URLError, TimeoutError, OSError):
        sys.exit("Không kết nối được Pay2S hoặc hết thời gian chờ. Không tự động gửi lại.")
    except (ValueError, UnicodeError):
        sys.exit("Pay2S trả phản hồi không phải JSON hợp lệ.")

    if not isinstance(result, dict) or result.get("status") is not True:
        sys.exit("Pay2S chưa xác nhận mô phỏng thành công; kiểm tra đơn trên Pay2S.")
    data = result.get("data")
    if not isinstance(data, dict) or data.get("orderId") != order_id or data.get("environment") != "demo":
        sys.exit("Phản hồi không khớp đơn demo; chưa thể xác nhận kết quả.")
    # Only print known status values, never arbitrary provider responses or credentials.
    print("Pay2S đã nhận mô phỏng demo.")
    if data.get("orderStatus") == "completed":
        print("Trạng thái đơn Pay2S: completed")
    ipn_status = data.get("ipnStatus")
    if ipn_status in ("queued", "sending", "sent", "retrying", "failed"):
        print("Trạng thái gửi IPN:", ipn_status)
    print("Giữ tunnel hoạt động, đợi IPN rồi tải lại hợp đồng trên SAM.")
    print("Kết quả này chưa xác nhận SAM đã ghi nhận tiền; kiểm tra trạng thái trên SAM.")


if __name__ == "__main__":
    try:
        main()
    except (KeyboardInterrupt, EOFError):
        sys.exit("Đã hủy.")
    except OSError:
        sys.exit("Không đọc được cấu hình local; kiểm tra quyền truy cập sam-be/.env.")
