-- Seed data for Course: Python Fundamentals (Course -> Chapter -> Lesson -> LearningMaterial)
-- Target schema: V002__create_course_learning_schema.sql

-- =============================================================================
-- 1. COURSE: Python Fundamentals
-- =============================================================================
INSERT INTO public.courses (
    id,
    instructor_id,
    title,
    description,
    prerequisite_description,
    publication_status,
    adaptive_enabled,
    adaptive_settings
) VALUES (
    '10000000-0000-4000-8000-000000000001',
    '1eacfb14-e494-596c-bb53-3d012e7caf72',
    'Python Fundamentals',
    'Khóa học nền tảng lập trình Python từ cơ bản đến thực hành: làm quen với biến, kiểu dữ liệu, cấu trúc điều khiển, vòng lặp, hàm, cấu trúc dữ liệu (List, Tuple, Dictionary, Set) và thao tác nhập xuất tệp tin.',
    'Không yêu cầu kiến thức lập trình trước đó. Phù hợp cho người mới bắt đầu.',
    'published',
    false,
    NULL
)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- 2. CHAPTERS (1 .. 5)
-- =============================================================================
INSERT INTO public.chapters (
    id,
    course_id,
    title,
    position
) VALUES
    ('20000000-0000-4000-8000-000000000001', '10000000-0000-4000-8000-000000000001', 'Chapter 1: Biến và các kiểu dữ liệu cơ bản', 1),
    ('20000000-0000-4000-8000-000000000002', '10000000-0000-4000-8000-000000000001', 'Chapter 2: Cấu trúc điều kiện và vòng lặp', 2),
    ('20000000-0000-4000-8000-000000000003', '10000000-0000-4000-8000-000000000001', 'Chapter 3: Hàm / Functions', 3),
    ('20000000-0000-4000-8000-000000000004', '10000000-0000-4000-8000-000000000001', 'Chapter 4: Cấu trúc dữ liệu', 4),
    ('20000000-0000-4000-8000-000000000005', '10000000-0000-4000-8000-000000000001', 'Chapter 5: I/O', 5)
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- 3. LESSONS
-- =============================================================================

-- Chapter 1: Biến và các kiểu dữ liệu cơ bản
INSERT INTO public.lessons (
    id,
    chapter_id,
    title,
    content_markdown,
    position,
    publication_status
) VALUES
    (
        '30000000-0000-4000-8000-000000000101',
        '20000000-0000-4000-8000-000000000001',
        'Bài 1.1: Khai báo biến và quy tắc đặt tên',
        '# Khai báo biến trong Python

Trong Python, biến được tạo ra ngay khi bạn gán giá trị cho nó bằng toán tử `=`. Không cần khai báo kiểu dữ liệu tường minh trước.

```python
student_name = "Nguyen Van A"
age = 20
```

## Quy tắc đặt tên biến
- Phải bắt đầu bằng một chữ cái hoặc ký tự gạch dưới (`_`).
- Không được bắt đầu bằng chữ số và không trùng với từ khóa của Python (`if`, `for`, `def`, ...).
- Khuyến nghị sử dụng quy chuẩn `snake_case` cho tên biến.',
        1,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000102',
        '20000000-0000-4000-8000-000000000001',
        'Bài 1.2: Các kiểu dữ liệu số (int, float)',
        '# Kiểu dữ liệu số trong Python

Python hỗ trợ hai kiểu số thông dụng nhất là số nguyên (`int`) và số thực (`float`).

```python
a = 10      # int
b = 3.14    # float
sum_val = a + b
power_val = a ** 2
```

Các phép toán số học cơ bản: `+`, `-`, `*`, `/`, `//` (chia lấy phần nguyên), `%` (chia lấy dư), `**` (lũy thừa).',
        2,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000103',
        '20000000-0000-4000-8000-000000000001',
        'Bài 1.3: Kiểu dữ liệu chuỗi (str) và thao tác chuỗi',
        '# Kiểu dữ liệu chuỗi (String)

Chuỗi ký tự trong Python được đặt trong cặp dấu nháy đơn `''...''` hoặc nháy kép `"..."`.

```python
greeting = "Hello, CodeMastery!"
print(greeting.upper())
print(greeting[0:5])
```

Bạn có thể sử dụng f-strings (`f"Xin chào {name}"`) để định dạng chuỗi ngắn gọn và trực quan.',
        3,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000104',
        '20000000-0000-4000-8000-000000000001',
        'Bài 1.4: Kiểu dữ liệu logic (boolean) và toán tử so sánh',
        '# Kiểu dữ liệu Boolean

Kiểu `bool` chỉ nhận một trong hai giá trị: `True` hoặc `False`.

```python
is_active = True
has_passed = (85 >= 50)
```

Các toán tử logic gồm `and`, `or`, `not` kết hợp với các toán tử so sánh `==`, `!=`, `<`, `>`, `<=`, `>=`.',
        4,
        'published'
    )
ON CONFLICT (id) DO NOTHING;

-- Chapter 2: Cấu trúc điều kiện và vòng lặp
INSERT INTO public.lessons (
    id,
    chapter_id,
    title,
    content_markdown,
    position,
    publication_status
) VALUES
    (
        '30000000-0000-4000-8000-000000000201',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.1: Câu lệnh điều kiện if',
        '# Câu lệnh điều kiện if

Câu lệnh `if` kiểm tra một biểu thức logic và thực thi khối lệnh thụt lề bên dưới nếu điều kiện đúng (`True`).

```python
score = 90
if score >= 80:
    print("Đạt loại Giỏi")
```',
        1,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000202',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.2: Cấu trúc rẽ nhánh if-else',
        '# Cấu trúc rẽ nhánh if-else

Cấu trúc `if-else` cho phép xử lý hai nhánh loại trừ nhau khi điều kiện đúng hoặc sai.

```python
number = 7
if number % 2 == 0:
    print("Số chẵn")
else:
    print("Số lẻ")
```',
        2,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000203',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.3: Cấu trúc đa nhánh if-elif-else',
        '# Cấu trúc đa nhánh elif

Sử dụng `elif` để kiểm tra tuần tự nhiều điều kiện khác nhau.

```python
gpa = 3.4
if gpa >= 3.6:
    rank = "Xuất sắc"
elif gpa >= 3.2:
    rank = "Giỏi"
elif gpa >= 2.5:
    rank = "Khá"
else:
    rank = "Trung bình"
```',
        3,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000204',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.4: Vòng lặp for (for loop)',
        '# Vòng lặp for với range()

Vòng lặp `for` kết hợp với hàm `range(start, stop, step)` thường dùng để lặp với số lần xác định trước.

```python
for i in range(1, 6):
    print(f"Lần lặp thứ {i}")
```',
        4,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000205',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.5: Vòng lặp while (while loop)',
        '# Vòng lặp while

Vòng lặp `while` tiếp tục thực thi khối lệnh chừng nào điều kiện kiểm tra còn mang giá trị `True`.

```python
count = 0
while count < 3:
    print(count)
    count += 1
```',
        5,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000206',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.6: Mô phỏng vòng lặp do-while trong Python',
        '# Mô phỏng do-while trong Python

Python không có từ khóa `do-while` riêng, nhưng ta có thể mô phỏng hành vi thực thi ít nhất một lần bằng `while True` kết hợp với `break`.

```python
attempts = 0
while True:
    attempts += 1
    if attempts >= 1:
        break
```',
        6,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000207',
        '20000000-0000-4000-8000-000000000002',
        'Bài 2.7: Duyệt phần tử với for-each',
        '# Duyệt trực tiếp phần tử (for-each)

Trong Python, vòng lặp `for item in iterable:` hoạt động như cấu trúc `for-each`, giúp duyệt qua từng phần tử của chuỗi hoặc tập hợp.

```python
languages = ["Python", "Java", "TypeScript"]
for lang in languages:
    print(lang)
```',
        7,
        'published'
    )
ON CONFLICT (id) DO NOTHING;

-- Chapter 3: Hàm / Functions
INSERT INTO public.lessons (
    id,
    chapter_id,
    title,
    content_markdown,
    position,
    publication_status
) VALUES
    (
        '30000000-0000-4000-8000-000000000301',
        '20000000-0000-4000-8000-000000000003',
        'Bài 3.1: Định nghĩa và gọi hàm',
        '# Định nghĩa hàm với từ khóa def

Hàm giúp đóng gói đoạn mã có thể tái sử dụng nhiều lần.

```python
def greet():
    print("Welcome to Python Fundamentals!")

greet()
```',
        1,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000302',
        '20000000-0000-4000-8000-000000000003',
        'Bài 3.2: Tham số truyền vào và giá trị trả về (return)',
        '# Tham số và câu lệnh return

Hàm có thể nhận các tham số đầu vào (bao gồm tham số mặc định) và trả về kết quả qua từ khóa `return`.

```python
def calculate_area(width, height=1.0):
    return width * height

result = calculate_area(5, 3)
```',
        2,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000303',
        '20000000-0000-4000-8000-000000000003',
        'Bài 3.3: Phạm vi biến (Scope)',
        '# Phạm vi biến (Local vs Global Scope)

Biến được khai báo bên trong hàm có phạm vi cục bộ (local scope) và chỉ tồn tại trong thời gian hàm thực thi, khác với biến toàn cục (global scope) bên ngoài hàm.

```python
total = 100  # Global

def add_bonus():
    bonus = 20  # Local
    return total + bonus
```',
        3,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000304',
        '20000000-0000-4000-8000-000000000003',
        'Bài 3.4: Hàm ẩn danh (Lambda Functions)',
        '# Hàm ẩn danh Lambda

Biểu thức `lambda` dùng để khởi tạo nhanh các hàm nhỏ gọn trên một dòng duy nhất.

```python
square = lambda x: x * x
print(square(6))
```',
        4,
        'published'
    )
ON CONFLICT (id) DO NOTHING;

-- Chapter 4: Cấu trúc dữ liệu
INSERT INTO public.lessons (
    id,
    chapter_id,
    title,
    content_markdown,
    position,
    publication_status
) VALUES
    (
        '30000000-0000-4000-8000-000000000401',
        '20000000-0000-4000-8000-000000000004',
        'Bài 4.1: Danh sách (List)',
        '# Danh sách (List)

`list` là cấu trúc dữ liệu có thứ tự và có thể thay đổi (mutable), hỗ trợ truy cập qua chỉ số (index), cắt lát (slicing) và các phương thức `append()`, `pop()`, `sort()`.

```python
scores = [7.5, 8.0, 9.5]
scores.append(10.0)
```',
        1,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000402',
        '20000000-0000-4000-8000-000000000004',
        'Bài 4.2: Bộ giá trị bất biến (Tuple)',
        '# Tuple trong Python

`tuple` là tập hợp có thứ tự nhưng bất biến (immutable) sau khi khởi tạo, thích hợp để lưu các bản ghi cố định như tọa độ hoặc cấu hình.

```python
point = (10, 20)
x, y = point
```',
        2,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000403',
        '20000000-0000-4000-8000-000000000004',
        'Bài 4.3: Từ điển (Dictionary)',
        '# Từ điển (Dictionary)

`dict` lưu trữ dữ liệu theo cặp khóa - giá trị (`key: value`), cho phép tra cứu nhanh theo khóa duy nhất.

```python
student = {"id": "SV01", "name": "An", "gpa": 3.5}
print(student.get("name"))
```',
        3,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000404',
        '20000000-0000-4000-8000-000000000004',
        'Bài 4.4: Tập hợp (Set)',
        '# Tập hợp (Set)

`set` là tập hợp không thứ tự chứa các phần tử duy nhất (loại bỏ trùng lặp) và hỗ trợ các phép toán tập hợp như hợp (`|`), giao (`&`), hiệu (`-`).

```python
unique_tags = {"python", "coding", "python"}
print(len(unique_tags))  # 2
```',
        4,
        'published'
    )
ON CONFLICT (id) DO NOTHING;

-- Chapter 5: I/O
INSERT INTO public.lessons (
    id,
    chapter_id,
    title,
    content_markdown,
    position,
    publication_status
) VALUES
    (
        '30000000-0000-4000-8000-000000000501',
        '20000000-0000-4000-8000-000000000005',
        'Bài 5.1: Nhập xuất chuẩn với input() và print()',
        '# Nhập xuất dữ liệu từ bàn phím và màn hình

Hàm `input()` luôn đọc dữ liệu đầu vào dưới dạng chuỗi (`str`), cần ép kiểu khi tính toán số học. Hàm `print()` dùng để hiển thị kết quả ra thiết bị xuất chuẩn.

```python
name = input("Nhập tên của bạn: ")
print(f"Xin chào, {name}!")
```',
        1,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000502',
        '20000000-0000-4000-8000-000000000005',
        'Bài 5.2: Làm việc với tệp văn bản (Text Files)',
        '# Đọc và ghi tệp văn bản (Text Files)

Sử dụng câu lệnh `with open(...)` (context manager) để đảm bảo tệp tin luôn được đóng tự động sau khi xử lý xong.

```python
with open("notes.txt", "w", encoding="utf-8") as f:
    f.write("Hello Python I/O\n")
```',
        2,
        'published'
    ),
    (
        '30000000-0000-4000-8000-000000000503',
        '20000000-0000-4000-8000-000000000005',
        'Bài 5.3: Xử lý dữ liệu JSON cơ bản',
        '# Làm việc với định dạng JSON

Mô-đun chuẩn `json` của Python cung cấp `json.loads()`, `json.dumps()`, `json.load()` và `json.dump()` để chuyển đổi giữa chuỗi/tệp JSON và cấu trúc `dict`/`list`.

```python
import json

data = {"course": "Python Fundamentals", "chapters": 5}
json_str = json.dumps(data, ensure_ascii=False)
```',
        3,
        'published'
    )
ON CONFLICT (id) DO NOTHING;

-- =============================================================================
-- 4. LEARNING MATERIALS
-- =============================================================================
INSERT INTO public.learning_materials (
    id,
    lesson_id,
    title,
    material_type,
    publication_status,
    text_content,
    object_key,
    original_filename,
    external_video_url
) VALUES (
    '40000000-0000-4000-8000-000000000101',
    '30000000-0000-4000-8000-000000000101',
    'Slide bài giảng 1.1: Biến và các kiểu dữ liệu cơ bản',
    'file',
    'published',
    NULL,
    'sample-data/materials/sample-slides.pdf',
    'sample-slides.pdf',
    NULL
)
ON CONFLICT (id) DO NOTHING;
