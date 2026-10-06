**Proposal**

CO3103 – Programming Integration Project, Semester 261 · Online Learning Platform

------------------------------------------------------------------------

# 1. Product Overview

| **Hạng mục** | **Nội dung đã chốt** |
|----|----|
| **Product name** | **Mastery-Based Programming Learning Platform for Beginner University Students** |
| **Mô hình** | Website e-learning theo khóa học (course-based), self-paced, có Instructor. Đây là một LMS chuyên biệt cho lập trình nhập môn, với lớp học thích ứng (adaptive) nằm trong luồng học chính. |
| **Nội dung giảng dạy** | Nhập môn lập trình với khóa mẫu Python Fundamentals (khoảng 20–25 skills: variables, expressions, conditionals, loops, functions, lists, strings, dictionaries, debugging cơ bản). Ngôn ngữ chỉ là dữ liệu nội dung, nền tảng không phụ thuộc vào Python. |
| **Learning context** | Giáo dục đại học, môn lập trình nhập môn, kết hợp tự học ngoài giờ và khóa do giảng viên quản lý. |
| **Target users** | Sinh viên đại học mới học lập trình (Learner), giảng viên nhập môn lập trình (Instructor), quản trị viên hệ thống (Administrator). |
| **Problem** | Sinh viên mới có nền tảng rất khác nhau nhưng thường học cùng một progression cố định. Người thiếu prerequisite vẫn bị đẩy sang bài khó hơn và dần hổng kiến thức. Người đã vững phải lặp lại nội dung quá cơ bản. |
| **Product value** | Learner biết chính xác skill nào còn yếu, được đề xuất bài bổ trợ đúng prerequisite và bỏ qua phần đã thành thạo. Instructor thấy được mức mastery của từng learner thay vì chỉ điểm tổng. |

# 2. Product Differentiation

| **Reference Model** | **Typical Approach** | **Our Product Difference** |
|----|----|----|
| **Moodle** | LMS mở, quản lý course/activity, phân quyền, mở rộng bằng plugin. Hạn chế: lộ trình mặc định là một cấu trúc chung. Việc điều chỉnh theo từng learner phải do giảng viên cấu hình điều kiện thủ công. | Skill graph và prerequisite là một phần của course model. Hệ thống tự cập nhật mastery sau mỗi bài đánh giá và tự đề xuất bước học tiếp theo, giảng viên không phải cấu hình từng trường hợp. |
| **Coursera** | Catalogue đa lĩnh vực, khóa học tuần tự cho số đông. Hạn chế: mọi learner đi qua cùng một trình tự. | Tập trung một ngữ cảnh hẹp (lập trình nhập môn đại học). Trình tự học thay đổi theo kết quả từng learner. |
| **edX** | Khóa độc lập hoặc chương trình nhiều khóa, self-paced, assessment có điểm, chứng chỉ. Hạn chế: tiến độ đo theo hoàn thành nội dung, không theo mastery từng skill. | Tiến độ đo bằng mastery của skill. Hoàn thành bài học và thành thạo skill là hai thông tin khác nhau, được theo dõi riêng. |
| **Khan Academy** | Học theo mức thành thạo, theo dõi skill, hỗ trợ cá nhân hóa. Đây là mô hình gần nhất với ý tưởng của ta. Nó là thư viện nội dung công khai, không gắn với lớp/giảng viên. | Đưa mastery-based learning vào bối cảnh **lớp đại học có Instructor**: enrolment, deadline, assignment, Q&A. Instructor tự định nghĩa skill graph cho môn của mình. Mỗi đề xuất kèm lý do dựa trên prerequisite. |

# 3. Actors

| **Actor** | **Goal** | **Main Responsibilities** |
|----|----|----|
| **Learner** | Học đúng trình độ thực tế, biết mình yếu skill nào. | Tìm và đăng ký khóa học, làm diagnostic và quiz, học theo đề xuất, nộp assignment, hỏi đáp, xem mastery map và tiến độ. |
| **Instructor** | Xây dựng khóa học và biết learner đang hổng ở đâu. | Tạo course/lesson/material, định nghĩa skill graph và gắn skill cho lesson/question, tạo quiz/assignment, chấm bài và feedback, trả lời Q&A, theo dõi mastery của learner. |
| **Administrator** | Vận hành hệ thống an toàn, có thể kiểm soát. | Quản lý user và course, xử lý báo cáo nội dung, xem metrics, kiểm tra audit log. |

# 4. Functional Scope

## 4.1 Accounts and Authorisation

- Registration (Learner tự đăng ký; Admin gán role Instructor), login/logout, password recovery.

- User profile.

- Role-based access control cho cả giao diện lẫn API.

- Account status management (active/locked).

## 4.2 Courses and Content

- Course catalogue có search, filter, pagination.

- Course detail: syllabus, instructor, prerequisites.

- Cấu trúc chapter → lesson.

- Hai loại material trở lên: text (Markdown), slides/attachments (PDF), video embed (link ngoài).

- Instructor tạo/sửa/publish/hide course và nội dung.

## 4.3 Enrolment and Learning

- Enrol/leave course.

- Học theo thứ tự mặc định của course, đánh dấu hoàn thành lesson.

- Lưu và hiển thị progress, resume từ vị trí gần nhất.

- Danh sách course in-progress và completed.

## 4.4 Assignments and Assessment

- Quiz có time limit, chấm tự động. Câu hỏi dạng single/multi-choice, true/false, "predict the output".

- File-submission assignment có deadline.

- Instructor xem bài nộp, chấm điểm, feedback.

- Learner xem kết quả và attempt history.

## 4.5 Interaction and Notifications

- Q&A theo từng lesson.

- Notification trong ứng dụng: nội dung mới, sắp đến deadline, có kết quả, có phản hồi Q&A.

- Report nội dung không phù hợp.

## 4.6 Administration and Basic Reporting

- Quản lý user và course.

- Xử lý content report.

- Metrics: enrolment, completion rate, learning outcome (điểm trung bình, phân bố mastery).

- Audit log cho các hành động quản trị quan trọng.

## 4.7 Adaptive Learning (Direction 3)

- Instructor định nghĩa skill, prerequisite giữa các skill, và ánh xạ lesson/question với skill.

- Placement diagnostic khi bắt đầu khóa.

- Cập nhật mastery sau mỗi quiz.

- Đề xuất bước tiếp theo (bỏ qua, bổ trợ, tiếp tục) kèm lý do, và chọn độ khó quiz luyện tập dựa trên mastery hiện tại.

- Mastery map cho Learner; Instructor xem mastery và lịch sử đề xuất của từng learner.

# 5. Core User Stories and Acceptance Criteria

<table>
<colgroup>
<col style="width: 7%" />
<col style="width: 13%" />
<col style="width: 26%" />
<col style="width: 51%" />
</colgroup>
<thead>
<tr>
<th><p><strong>ID</strong></p></th>
<th><p><strong>Actor</strong></p></th>
<th><p><strong>User Story</strong></p></th>
<th><p><strong>Acceptance Criteria</strong></p></th>
</tr>
</thead>
<tbody>
<tr>
<td><p><strong>US-01</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn đăng ký, đăng nhập và khôi phục mật khẩu để có thể truy cập an toàn vào không gian học tập của riêng mình.</p></td>
<td><ul>
<li><p>Given email hợp lệ và chưa được sử dụng cùng một mật khẩu đáp ứng quy tắc, when tôi đăng ký, sau đó hệ thống tạo tài khoản với role Learner và mật khẩu được lưu dưới dạng hash.</p></li>
<li><p>Given thông tin đăng nhập sai hoặc tài khoản đang bị locked, when tôi đăng nhập, then truy cập bị từ chối kèm một thông báo lỗi chung (generic error).</p></li>
<li><p>Given một Learner session, when gọi các chức năng của Instructor/Admin, then hệ thống phản hồi "forbidden".</p></li>
<li><p>Given một yêu cầu đặt lại mật khẩu (reset request), then một liên kết có thời hạn cho phép tôi đặt mật khẩu mới.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-02</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn tìm kiếm trong catalogue và enrol vào một course để có thể bắt đầu học.</p></td>
<td><ul>
<li><p>Search, filter và pagination hoạt động trên các course đã published; trang course detail hiển thị syllabus, instructor và prerequisites.</p></li>
<li><p>When tôi enrol, course xuất hiện trong "My courses" và nội dung của course có thể truy cập được.</p></li>
<li><p>Enrol vào một course đang hidden/unpublished sẽ bị từ chối.</p></li>
<li><p>Sau khi rời khỏi một course (leave), course đó biến mất khỏi "My courses".</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-03</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn làm một diagnostic quiz ngắn ở đầu khóa để hệ thống biết tôi đã thành thạo (master) những gì.</p></td>
<td><ul>
<li><p>Given tôi đã enrol nhưng chưa có mastery data, when tôi mở course, then hệ thống đề nghị làm diagnostic (bỏ qua = bắt đầu như người mới, theo default path).</p></li>
<li><p>Sau khi nộp bài, trạng thái mastery ban đầu được thiết lập cho mọi skill nằm trong phạm vi của diagnostic.</p></li>
<li><p>Kết quả hiển thị ngay trên mastery map của tôi.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-04</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn học các lesson, đánh dấu hoàn thành và tiếp tục học sau, để theo dõi được progress của mình.</p></td>
<td><ul>
<li><p>Đánh dấu hoàn thành một lesson sẽ cập nhật phần trăm progress.</p></li>
<li><p>"Resume" mở lesson được truy cập gần nhất.</p></li>
<li><p>Các lesson được bỏ qua nhờ mastery đã được chứng minh (proven mastery) được tính là hoàn thành và gắn nhãn "Mastered".</p></li>
<li><p>When mọi lesson bắt buộc đã hoàn thành, course chuyển sang trạng thái "Completed".</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-05</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn làm các timed quiz được chấm điểm tự động để nhận feedback ngay lập tức.</p></td>
<td><ul>
<li><p>Quiz tự động nộp (auto-submit) khi hết time limit.</p></li>
<li><p>Điểm số khớp với answer key trên bộ dữ liệu kiểm thử (seeded test set).</p></li>
<li><p>Trang kết quả hiển thị đúng/sai của từng câu hỏi; attempt history liệt kê ngày làm, điểm số và số thứ tự lần làm (attempt number).</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-06</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn hệ thống cập nhật skill mastery của tôi sau mỗi quiz và gợi ý nên học gì tiếp theo, để tôi bỏ qua phần đã biết và bù đắp phần còn thiếu.</p></td>
<td><ul>
<li><p>Given một skill-tagged quiz đã được nộp, then mastery của các skill được kiểm tra sẽ được cập nhật và phản ánh trên mastery map.</p></li>
<li><p>Given một skill ở trạng thái Mastered, then các lesson chỉ bao phủ những skill đã mastered được đánh dấu "skippable (test-out)".</p></li>
<li><p>Given một skill ở trạng thái Weak và prerequisite của nó chưa Mastered, then một remedial lesson cho prerequisite đó được đề xuất trước, kèm reason text (lý do đề xuất).</p></li>
<li><p>Given một prerequisite vẫn chưa mastered, then việc mở một advanced lesson sẽ hiển thị cảnh báo nhưng không bị chặn.</p></li>
<li><p>Given hai seeded profile (một profile vững và một profile hổng ở Loops) trên cùng một course, then các lesson được đề xuất tiếp theo của hai profile này khác nhau.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-07</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn xem một mastery map để hiểu rõ điểm mạnh và lỗ hổng kiến thức của mình.</p></td>
<td><ul>
<li><p>Mỗi skill của course được hiển thị cùng trạng thái (Unknown / Learning / Mastered / Weak) và các liên kết prerequisite của nó.</p></li>
<li><p>Map phản ánh kết quả assessment mới nhất.</p></li>
<li><p>Chọn một skill sẽ hiển thị các lesson liên quan và điểm số gần nhất.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-08</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn nộp file assignment và xem feedback để biết cách cải thiện.</p></td>
<td><ul>
<li><p>Given trước deadline, file của tôi được chấp nhận; sau deadline, bài nộp bị từ chối.</p></li>
<li><p>Sau khi được chấm điểm, tôi có thể xem điểm số và feedback, đồng thời nhận được notification.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-09</strong></p></td>
<td><p>Learner</p></td>
<td><p>Với vai trò learner, tôi muốn đặt câu hỏi trong lesson và được thông báo khi có phản hồi hoặc sắp đến deadline, để không bị mắc kẹt hoặc trễ hạn.</p></td>
<td><ul>
<li><p>Câu hỏi đăng trên một lesson hiển thị trong Q&amp;A của lesson đó.</p></li>
<li><p>When có người trả lời, người đặt câu hỏi nhận được notification.</p></li>
<li><p>Notification được tạo ra khi có nội dung mới, khi sắp đến deadline và khi có kết quả được công bố.</p></li>
<li><p>Tôi có thể report một bài đăng; bài đó xuất hiện trong hàng đợi report của admin (admin report queue).</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-10</strong></p></td>
<td><p>Instructor</p></td>
<td><p>Với vai trò instructor, tôi muốn xây dựng và publish nội dung course để learner có thể học.</p></td>
<td><ul>
<li><p>Tôi có thể tạo course → chapters → lessons và đính kèm ít nhất hai loại material.</p></li>
<li><p>Course/lesson đang hidden không hiển thị với learner; khi publish thì hiển thị.</p></li>
<li><p>Tôi chỉ có thể chỉnh sửa các course do tôi sở hữu.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-11</strong></p></td>
<td><p>Instructor</p></td>
<td><p>Với vai trò instructor, tôi muốn định nghĩa skill, prerequisite và map lesson cũng như câu hỏi vào skill, để hệ thống có thể điều chỉnh (adapt) lộ trình học.</p></td>
<td><ul>
<li><p>Tôi có thể tạo skill và các liên kết prerequisite; hệ thống từ chối liên kết tạo ra chu trình (cycle).</p></li>
<li><p>Mỗi lesson được map với ít nhất một skill; mỗi câu hỏi được gắn tag một skill và một mức độ khó (difficulty level).</p></li>
<li><p>Adaptive mode chỉ có thể được bật khi mọi lesson đã published đều được map và mỗi skill có đủ số lượng câu hỏi tối thiểu.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-12</strong></p></td>
<td><p>Instructor</p></td>
<td><p>Với vai trò instructor, tôi muốn tạo quiz và assignment để learner có thể được đánh giá.</p></td>
<td><ul>
<li><p>Một quiz có time limit và các câu hỏi được gắn skill tag; một assignment có deadline.</p></li>
<li><p>Chỉ các quiz/assignment đã published mới hiển thị với learner đã enrol.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-13</strong></p></td>
<td><p>Instructor</p></td>
<td><p>Với vai trò instructor, tôi muốn chấm bài nộp và đưa ra feedback để learner nhận được sự hướng dẫn.</p></td>
<td><ul>
<li><p>Tôi chỉ có thể xem danh sách và tải xuống các bài nộp thuộc course của chính mình.</p></li>
<li><p>Lưu điểm kèm feedback sẽ làm điểm hiển thị với learner và kích hoạt notification.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-14</strong></p></td>
<td><p>Instructor</p></td>
<td><p>Với vai trò instructor, tôi muốn xem progress, mastery map và recommendation history của từng learner, để hiểu và kiểm chứng các quyết định adaptive.</p></td>
<td><ul>
<li><p>Với một learner trong course của tôi, tôi xem được progress, điểm số và mastery map.</p></li>
<li><p>Một recommendation log hiển thị loại (type), lý do (reason) và thời điểm của từng đề xuất.</p></li>
<li><p>Tôi không thể xem các learner chưa enrol vào course của tôi.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-15</strong></p></td>
<td><p>Administrator</p></td>
<td><p>Với vai trò administrator, tôi muốn quản lý user để kiểm soát được quyền truy cập.</p></td>
<td><ul>
<li><p>Tôi có thể tìm kiếm user, đổi role và lock/unlock tài khoản.</p></li>
<li><p>User bị locked không thể đăng nhập.</p></li>
<li><p>Mỗi hành động được ghi vào audit log.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-16</strong></p></td>
<td><p>Administrator</p></td>
<td><p>Với vai trò administrator, tôi muốn xem xét các nội dung bị report để xử lý nội dung không phù hợp.</p></td>
<td><ul>
<li><p>Các report xuất hiện trong một queue kèm status.</p></li>
<li><p>Hide nội dung sẽ gỡ nội dung đó khỏi giao diện của learner; dismiss sẽ đóng report.</p></li>
<li><p>Mỗi quyết định được ghi vào audit log.</p></li>
</ul></td>
</tr>
<tr>
<td><p><strong>US-17</strong></p></td>
<td><p>Administrator</p></td>
<td><p>Với vai trò administrator, tôi muốn xem các metrics cơ bản và audit log để giám sát nền tảng.</p></td>
<td><ul>
<li><p>Dashboard hiển thị số lượng enrolment theo từng course, completion rate, điểm quiz trung bình và phân bố trạng thái mastery theo từng skill.</p></li>
<li><p>Audit log liệt kê actor, action, target và timestamp, và có thể được lọc (filter).</p></li>
</ul></td>
</tr>
</tbody>
</table>

# 6. Advanced Component

**Selected direction:** Direction 3 – Mastery-Based Adaptive Learning.

**Problem addressed:** Learner có prerequisite khác nhau nhưng đi cùng một progression cố định, nên người yếu bị hổng dần và người giỏi lãng phí thời gian. Cần một cơ chế biết learner đang nắm skill nào và điều chỉnh đường học theo đó.

**Main role in product:** Đây là lớp điều phối bước học tiếp theo của khóa. Nó không phải một tính năng riêng: chính nó quyết định "Next recommended" trên dashboard, các nhãn Skippable/Remedial trong danh sách lesson, và mastery map.

**Độ khó chính:** mô hình hóa skill graph đúng, tạo question bank có gắn skill/độ khó đủ dùng, và giải thích được vì sao hệ thống đưa ra đề xuất. Cơ chế dùng luật (rule-based) với ngưỡng cấu hình được, không cần machine learning. Business rule contract: kết quả đánh giá được ánh xạ vào một trong bốn trạng thái mastery bằng các ngưỡng cấu hình; trạng thái cuối cùng là đầu vào trực tiếp cho adaptive decision. Ngưỡng cụ thể là cấu hình/implementation detail và phải được dùng nhất quán giữa assessment, mastery engine và evaluation.

**Integration flow:**

> Enrol → Placement diagnostic → Initial mastery per skill
>
> ↓
>
> Learn lesson → Skill-tagged quiz (practice difficulty selected by current mastery)
>
> ↓
>
> Assessment result → Skill mastery update → Skill state (Unknown/Learning/Mastered/Weak)
>
> ↓
>
> Adaptive decision:
>
> Mastered → mark related lessons skippable (test-out)
>
> Weak + prerequisite not mastered → insert remedial lesson of that prerequisite
>
> Learning → continue canonical next lesson, with practice quiz at selected difficulty
>
> ↓
>
> Update "Next recommended" + LessonProgress + Mastery map + Notification (with reason)

Note: Manual assignments (file submission) are tracked for course completion grades but do not directly trigger real-time skill mastery updates in the Adaptive Engine, to keep mastery updates timely and avoid subjective-grading latency.

Thứ tự lesson mặc định của course vẫn là canonical order. Lớp adaptive không thay đổi cấu trúc course hay khóa cứng quyền truy cập; nó quyết định bước học được khuyến nghị cho từng learner, đồng thời có thể đánh dấu lesson đã mastered để test-out và đề xuất lesson remedial trước khi tiếp tục. Nhờ đó vẫn khớp yêu cầu học theo thứ tự của core scope nhưng learning flow có thể khác nhau giữa learner profiles.

**Required data:**

- Kết quả từng câu và từng attempt của learner.

- Skill và prerequisite relationships (đồ thị không chu trình cho mỗi course).

- Ánh xạ lesson–skill và question–skill–difficulty.

- Mastery hiện tại của mỗi learner theo skill.

- Log các đề xuất (loại, lý do, thời điểm) để giải thích và đánh giá.

- Seed data: khoảng 20–25 skills, khoảng 120 câu hỏi, và 3 learner profiles mô phỏng.

**Evaluation concept:**

- **Learner profiles:** (P1) đã vững kiến thức nền, (P2) vững cơ bản nhưng hổng ở Loops, (P3) người mới hoàn toàn. Dựng bằng dữ liệu mô phỏng có kịch bản, bổ sung pilot với một số sinh viên thật nếu có.

- **Baseline:** lộ trình tuyến tính cố định của cùng khóa học.

- **Metrics đề xuất** (ngưỡng sẽ hiệu chỉnh sau):

1.  **Path divergence:** số lesson được skip/chèn so với baseline cho từng profile (kỳ vọng P1, P2, P3 khác nhau rõ rệt).

2.  **Routing correctness:** tỷ lệ lỗ hổng prerequisite được cài sẵn mà hệ thống đề xuất đúng bài bổ trợ (mục tiêu ≥ 80%).

3.  **Mastery-update correctness:** tỷ lệ kịch bản kiểm thử cho ra đúng trạng thái skill như kỳ vọng.

4.  **Learning gain** (pilot hoặc mô phỏng): thay đổi điểm skill-check sau khi học bài bổ trợ, so với baseline.

- **Demo trực tiếp:** hai learner cùng khóa, kết quả quiz khác nhau, đường học và mastery map khác nhau.

# 7. High-Level Architecture

Kiến trúc: **web application dạng modular monolith**, tách ba lớp Frontend → Backend/API → Database + Storage.

> FRONTEND – React + TypeScript SPA (responsive)
>
> Learner UI │ Instructor UI │ Admin UI │ Mastery Map view
>
> │
>
> │ HTTPS · REST/JSON · JWT
>
> ▼
>
> BACKEND – Spring Boot (modular monolith)
>
> ├─ Auth & RBAC – accounts, roles, API protection
>
> ├─ Course & Learning – catalogue, lessons, enrolment, progress
>
> ├─ Assessment – quizzes, assignments, grading, attempts
>
> ├─ ★ Adaptive Learning – Skill Graph → Mastery Engine → Adaptive Decision (Next Lesson / Practice Difficulty)
>
> ├─ Interaction & Notify – Q&A, notifications, content reports
>
> └─ Admin & Reporting – users, metrics, audit log
>
> │ │
>
> ▼ ▼
>
> PostgreSQL Object storage (S3-compatible)
>
> (all domain data) (slides, attachments, submissions)

**Vị trí của Adaptive Learning trong luồng chính:**

- **Assessment → Adaptive: kết quả quiz và dữ liệu từng câu là đầu vào để cập nhật mastery. Giao tiếp qua event-driven (vd: Spring ApplicationEvent) — Assessment phát ra QuizSubmittedEvent, Adaptive lắng nghe và cập nhật, giữ tính loose-coupling giữa hai module.**

- **Adaptive → Course & Learning: adaptive decision xác định next recommendation, test-out/remedial guidance và các nhãn tương ứng; canonical course order vẫn được giữ làm base structure.**

- **Adaptive → Progress**: test-out/mastery decisions update LessonProgress so learner completion state stays consistent with adaptive learning.

- **Adaptive → Notification**: adaptive produces a recommendation/reason event; Notification module owns creation and delivery of the in-app notification.

- **Adaptive → Admin & Reporting**: reporting consumes mastery/progress data as learning-outcome metrics; it does not own adaptive decision logic.

- **Data Integrity (Transaction Management)**: luồng xử lý từ khi tính mastery đến khi cập nhật LessonProgress được bọc trong một database transaction thống nhất, tránh trạng thái nửa-vời giữa hai bước cập nhật.

Các module là các package có ranh giới rõ trong một ứng dụng, deploy như một khối. Chưa cần microservices ở quy mô này.

# 8. Technology Stack

| **Layer** | **Selected Technology** | **Reason** |
|----|----|----|
| **Frontend** | TypeScript + React (Vite), Material UI | Phổ biến, tài liệu tốt, dễ chia component theo role; MUI giúp giao diện responsive nhanh. |
| **Mastery map** | React Flow | Vẽ đồ thị skill và prerequisite có sẵn, tránh tự viết. |
| **Backend** | Java 25 LTS + Spring Boot 4.1.1 (Spring Data JPA, Bean Validation) | Nhóm mạnh Java; static typing giúp 6–7 người tích hợp ít lỗi; cấu trúc layered và package-per-module khớp kiến trúc. |
| **API style** | REST/JSON, tài liệu OpenAPI (springdoc) | Đơn giản, dễ test, làm tài liệu API cho deliverable cuối. |
| **Security** | Spring Security, JWT, BCrypt | RBAC và bảo vệ API mature; password được hash. |
| **Database** | PostgreSQL + Flyway migrations | Dữ liệu quan hệ chặt (user, enrolment, attempt, skill graph); migration có version. |
| **Storage** | MinIO (S3-compatible) cho slides/attachments/submissions; video qua embed link ngoài | Cùng một API cho dev và deploy; không phải tự làm hosting/transcoding video. |
| **Infrastructure** | Docker Compose; GitHub Actions (build + test); một cloud VM chạy Docker Compose | Cài lại được từ tài liệu, không phụ thuộc tài khoản cá nhân; CI/CD đúng yêu cầu Quality. |
| **Testing** | JUnit 5 + Mockito (business logic, mastery engine); Playwright (một E2E flow chính) | Đáp ứng yêu cầu test logic và E2E. |
| **Observability** | Spring Boot Actuator + structured logging | Đủ cho logging/metrics vận hành cơ bản. |

**Technology Selection Rationale:**

- **Năng lực nhóm:** Java/Spring Boot và TypeScript/React có nhiều tài liệu và lời giải lỗi. Thành viên mạnh C++ vẫn đóng góp được vào phần graph/thuật toán (kiểm tra chu trình, tính trạng thái) viết bằng Java.

- **Kiến trúc và scope:** monolith một ngôn ngữ backend, một database, một storage, dễ debug và chia module song song.

- **Advanced component: mastery engine và adaptive decision logic chạy cùng backend, không cần dịch vụ ML hay Python riêng. Skill graph lưu dạng bảng quan hệ trong PostgreSQL.**

- **Maintainability và deployment:** Docker Compose cho môi trường giống nhau giữa dev và demo; Flyway và OpenAPI giữ database và API có tài liệu.

# 9. High-Level Data Model

| **Domain Entity** | **Purpose** | **Main Relationship** |
|----|----|----|
| **User** | Tài khoản, role (Learner/Instructor/Admin) và trạng thái (active/locked). Role là thuộc tính vì chỉ có 3 role cố định. | 1–N với Course (Instructor), Enrollment, Attempt, Submission, Notification |
| **Course** | Khóa học, trạng thái publish/hide, course-level prerequisites (learner-facing description), adaptive_settings (JSON config ngưỡng mastery dùng bởi business rule contract ở mục 6). | 1–N Chapter; 1–N Skill; N–M User qua Enrollment |
| **Chapter** | Nhóm lesson theo thứ tự | N–1 Course; 1–N Lesson |
| **Lesson** | Đơn vị học, thứ tự mặc định | N–1 Chapter; 1–N LearningMaterial; N–M Skill qua LessonSkill |
| **LearningMaterial** | Text, slide/attachment, video link | N–1 Lesson; file trỏ tới object storage |
| **Enrollment** | Việc learner tham gia course, trạng thái, vị trí học gần nhất | N–1 User; N–1 Course; 1–N LessonProgress |
| **LessonProgress** | Tiến độ từng lesson (completed / mastered-skip) | N–1 Enrollment; N–1 Lesson |
| **Quiz** | Bài quiz (loại: diagnostic, luyện tập, kiểm tra), time limit | N–1 Course; N–M Question |
| **Question** | Câu hỏi và đáp án, kèm skill và độ khó | N–1 Skill; N–M Quiz |
| **Attempt** | Một lần làm quiz: điểm, thời gian, các câu đã trả lời | N–1 User; N–1 Quiz; 1–N AttemptAnswer |
| **AttemptAnswer** | Đáp án từng câu, đúng/sai, dùng để cập nhật mastery | N–1 Attempt; N–1 Question |
| **Assignment** | Bài nộp file, deadline | N–1 Course/Lesson |
| **Submission** | Bài nộp, điểm, feedback của Instructor | N–1 Assignment; N–1 User |
| **Skill** | Kỹ năng lập trình trong một course | N–1 Course; N–M Skill qua SkillPrerequisite |
| **SkillPrerequisite** | Quan hệ skill A yêu cầu skill B; structured dependency used by adaptive learning; đồ thị không chu trình. | Skill–Skill |
| **LearnerSkillMastery** | Mức mastery và trạng thái của learner theo từng skill | N–1 User; N–1 Skill |
| **PathRecommendation** | Log đề xuất: loại (skip/remedial/continue), lesson đích, lý do | N–1 Enrollment; N–1 Lesson |
| **DiscussionPost** | Q&A theo lesson (post gốc và reply) | N–1 Lesson; N–1 User; tự tham chiếu cho reply |
| **Notification** | Thông báo trong ứng dụng, trạng thái đã đọc | N–1 User |
| **ContentReport** | Báo cáo nội dung và trạng thái xử lý | N–1 User (reporter); trỏ tới DiscussionPost hoặc Lesson |
| **AuditLog** | Hành động quản trị: ai, làm gì, trên đối tượng nào, khi nào | N–1 User (actor) |

# 10. Feasibility and Scope Control

**Tuần 39–50:**

- Tuần 39–40: chốt requirements, UI prototype, kiến trúc, data model, dựng project foundation.

- Tuần 41–43: cài core features; dựng prototype mastery engine.

- Tuần 44: kiểm tra bản tích hợp và nộp interim report.

- Tuần 45–46: hoàn thiện core và advanced component; thu thập dữ liệu đánh giá.

- Tuần 47: testing và sửa lỗi.

- Tuần 48–49: ổn định deployment, hoàn thiện đánh giá và báo cáo, tập demo.

- Tuần 50: nộp final report và hoàn tất demo.

**Out-of-scope (giữ scope):** sandbox chạy code, ML/AI chatbot, chứng chỉ, thanh toán, hosting/transcoding video, email/SMS/push, chat real-time, native mobile app, adaptive xuyên nhiều course, phát hiện đạo văn.

**Rủi ro chính:** chất lượng và khối lượng nội dung (bài học, question bank có gắn skill). Cách giảm: cắt số skill xuống 15–20 nếu chậm, và chốt skill graph sớm ở tuần 39–40.
