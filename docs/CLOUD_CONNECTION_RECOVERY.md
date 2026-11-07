# Kết nối PostgreSQL và Redis qua dịch vụ cloud

## Đọc log và phạm vi kết luận

`HikariPool - Failed to validate connection ... This connection has been closed` cho biết connection trong pool đã đóng khi Hikari kiểm tra. Chỉ dòng log này chưa phân biệt được server/pooler đóng connection, lỗi mạng, host ngủ/thức hay sự cố từ provider.

`Redis health check failed: Connection reset` tiếp theo bởi `Reconnected` cho thấy Lettuce đã kết nối lại. Điều đó không tự chứng minh request đang chạy trước lúc reset đã thành công. Hai dịch vụ remote gián đoạn gần nhau gợi ý kiểm tra cả đường mạng/host chung; chưa đủ bằng chứng để quy kết nguyên nhân.

Không suy ra một giao dịch thanh toán thất bại hoặc thành công từ các log connection. Khi người dùng gặp lỗi giữa lúc tạo/thanh toán, tiếp tục kiểm tra checkout cũ theo idempotency key và inbox; không tự tạo thêm đơn hoặc thử lại thao tác tài chính tùy ý.

## Cấu hình hiện hành

Backend có các giá trị mặc định sau; có thể ghi đè bằng env mà không sửa mã nguồn:

```dotenv
DB_POOL_CONNECTION_TIMEOUT_MS=30000
DB_POOL_VALIDATION_TIMEOUT_MS=3000
DB_POOL_IDLE_TIMEOUT_MS=300000
DB_POOL_MAX_LIFETIME_MS=600000
DB_POOL_KEEPALIVE_TIME_MS=60000
DB_TCP_KEEPALIVE=true
REDIS_CONNECT_TIMEOUT=5s
REDIS_COMMAND_TIMEOUT=3s
```

- Hikari giữ connection tối đa 10 phút, kiểm tra connection nhàn rỗi mỗi khoảng 60 giây. Connection đang được dùng không bị cắt chỉ vì hết max lifetime. Các giá trị này là điểm khởi đầu của SAM, không phải timeout được Supabase cam kết.
- Idle timeout 5 phút chỉ thu hồi connection nhàn rỗi vượt mức minimum idle. Không đổi `DB_POOL_MAX` hoặc `DB_POOL_MIN` hiện có trong lần điều chỉnh này.
- `validation-timeout` phải thấp hơn `connection-timeout`; `keepalive-time` phải thấp hơn `max-lifetime`. Hikari yêu cầu keepalive/max lifetime khác 0 tối thiểu 30 giây. Khi biết giới hạn vòng đời connection của hạ tầng, đặt max lifetime thấp hơn giới hạn đó.
- TCP keepalive của pgJDBC được bật; nhịp TCP probe cụ thể còn phụ thuộc hệ điều hành. Keepalive không bảo đảm giữ được socket khi máy ngủ, đổi mạng hoặc provider đóng kết nối.
- Redis có timeout kết nối 5 giây và timeout lệnh 3 giây. Không thêm connection pool Redis, không thay cơ chế reconnect/replay của Lettuce, không tắt kiểm tra health hoặc TLS. `REDIS_SSL=true` khi dùng Upstash; giá trị false trong `.env.example` dành cho Redis local.
- Các tham số Hikari dùng mili giây; tham số Redis dùng duration có đơn vị. Env chuẩn `SPRING_DATASOURCE_HIKARI_*` và `SPRING_DATA_REDIS_*` nếu được cấu hình riêng cũng cần được kiểm tra vì có thể ghi đè cấu hình ứng dụng.

Rebuild/restart backend để áp dụng default mới. Nếu chỉ thay env cho image đã có bản sửa, cần recreate container để nạp env mới. Không cần migration hoặc thay khóa Pay2S cho thay đổi connection này.

## Kiểm tra khi lỗi lặp lại

1. Ghi thời điểm, tần suất và API bị ảnh hưởng; đối chiếu với host ngủ/thức, thay đổi Wi-Fi/VPN, restart container và log của provider. Theo dõi sau lỗi xem PostgreSQL có lấy được connection mới và Redis có tiếp tục reconnect thành công không.
2. Kiểm tra health DB/Redis và các metric Hikari active/idle/pending/timeout ở endpoint Actuator được bảo vệ. Xem connection usage và giới hạn trên Supabase, lỗi/quota trên Upstash; tổng pool của tất cả backend instance phải nằm trong ngân sách connection thực tế.
3. Backend chạy lâu dài dùng direct connection hoặc shared pooler session mode phù hợp kết nối IPv4. Không tự đổi port sang transaction mode chỉ để hết warning; chế độ kết nối phải tương thích với ứng dụng và Flyway.
4. Nếu cả hai dịch vụ vẫn reset đồng thời, tiếp tục điều tra hạ tầng/mạng. Việc giảm max lifetime không giải quyết mọi nguyên nhân reset và không bảo đảm mọi request trước đó đã được hoàn tất.

Nguồn chính thức: [HikariCP configuration](https://github.com/brettwooldridge/HikariCP#configuration-knobs-baby), [pgJDBC connection parameters](https://jdbc.postgresql.org/documentation/use/#connection-parameters), [Supabase pooling](https://supabase.com/docs/guides/database/connecting-to-postgres/pooling-and-limits), [Lettuce client options](https://redis.github.io/lettuce/advanced-usage/client-options/), [Upstash TLS/ECONNRESET](https://upstash.com/docs/redis/troubleshooting/econn_reset). Hướng dẫn Upstash về thiếu TLS là một trường hợp cụ thể; không coi mọi reset đều do thiếu TLS.
