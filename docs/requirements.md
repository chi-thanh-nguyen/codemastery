       CO3103 – PROGRAMMING INTEGRATION PROJECT
                Software Engineering Track – Semester 261




    DESIGN AND DEVELOPMENT
OF AN ONLINE LEARNING PLATFORM


    Format:     Team-based, 6–7 students per team
Deliverables:   Web application, source code, documentation, testing and a de-
                ployed build
  Language:     English for the reports, the presentation and the demo
       Start:   Week 38
        End:    Week 50




                      Project brief and assessment rubric
                                Semester 261
CO3103 – Programming Integration Project, Semester 261                                Online Learning Platform



Contents

1   Background and Objectives                                                                               4

2   Representative Reference Platforms                                                                      4

3   User Roles                                                                                              4

4   Mandatory Core Functional Scope                                                                         4
    4.1 Accounts and Authorisation . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        4
    4.2 Courses and Content . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .       5
    4.3 Enrolment and Learning . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        5
    4.4 Assignments and Assessment . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        5
    4.5 Interaction and Notifications . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .     5
    4.6 Administration and Basic Reporting . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        5

5   Mandatory Advanced Component                                                                            5
    5.1 Direction 1 – Grounded AI Teaching Assistant . . . . . . . . . . . . . . . . . . . . . . . .        6
    5.2 Direction 2 – Personalised Learning Paths and Recommendations . . . . . . . . . . . . .             6
    5.3 Direction 3 – Mastery-Based Adaptive Learning . . . . . . . . . . . . . . . . . . . . . . .         6
    5.4 Direction 4 – Automated Assessment and Academic Integrity . . . . . . . . . . . . . . . .           6
    5.5 Direction 5 – Learning Analytics and Early Warning . . . . . . . . . . . . . . . . . . . .          6
    5.6 Direction 6 – Real-Time Collaborative Classroom . . . . . . . . . . . . . . . . . . . . . .         6
    5.7 Direction 7 – Learning Anywhere: PWA, Offline and Accessibility . . . . . . . . . . . . .           6

6   Quality Requirements                                                                                    7

7   Suggested Architecture                                                                                  7

8   Milestones and Deliverables                                                                             7
    8.1 Official Assessment Schedule . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .        7
         8.1.1 Language . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .       8
         8.1.2 Submission Cut-Off and Late Policy . . . . . . . . . . . . . . . . . . . . . . . . .         8
    8.2 Proposal – 23:59 Sunday 20 September 2026, 20% . . . . . . . . . . . . . . . . . . . . .            8
    8.3 Interim Report – 23:59 Sunday 1 November 2026, 30% . . . . . . . . . . . . . . . . . . .            8
    8.4 Final Report and Demo – 23:59 Sunday 13 December 2026, 50% . . . . . . . . . . . . . .              9

9   Weekly Plan                                                                                             9

10 Team Formation and Registration                                                                          9
   10.1 Size and Responsibilities . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .     9
   10.2 Pre-Assigned Team List . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .     10
   10.3 Team-Change Rules . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .      12
   10.4 Registration Email . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .     13
   10.5 Team Registration Email Template . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .       13
   10.6 Interim Report Submission Email Template . . . . . . . . . . . . . . . . . . . . . . . . .         14
   10.7 Final Report and Demo Package Submission Email Template . . . . . . . . . . . . . . . .            14

11 Weekly Progress Google Doc Template                                                                     15
   11.1 Team Information . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .     15
   11.2 Project Information . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .    15
   11.3 Weekly Progress Log . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .      15



                                                         Page 2
CO3103 – Programming Integration Project, Semester 261                              Online Learning Platform



12 Assessment Rubrics                                                                                    15
   12.1 How the Rubrics Are Applied . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .      15
   12.2 Proposal Rubric – 20% . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .    16
   12.3 Interim Report Rubric – 30% . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .    16
   12.4 Final Report Rubric – 25% . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . . .    17
   12.5 Presentation and Demo Rubric – 25% . . . . . . . . . . . . . . . . . . . . . . . . . . . .       17

13 Minimum Acceptance Criteria                                                                           17




                                                         Page 3
CO3103 – Programming Integration Project, Semester 261                               Online Learning Platform



1. Background and Objectives

Each team designs and develops an online learning platform that serves a specific group of users or a
specific training context — for example university study, vocational skills, programming practice, foreign
languages, or in-house corporate training.
The project pursues the following objectives:
 • apply the software development process end to end: requirements elicitation, design, implementation,
   testing and deployment;
 • build a multi-role web system with complete data and business flows;
 • address a concrete learning need rather than merely cloning the interface of an existing platform;
 • demonstrate technical capability through at least one advanced feature that can be measured and shown
   live;
 • practise teamwork, source-code management, progress tracking and technical writing.

2. Representative Reference Platforms

The examples below are provided as references for product models and business flows. Teams must not
copy a platform wholesale; they must identify their target users and their product’s point of difference.

Platform             Aspects worth referencing
Coursera             A multi-disciplinary course catalogue, learning paths, practical assignments/projects
                     and certificates.
edX                  Standalone courses, multi-course programmes, self-paced study, graded assessments
                     and completion certificates.
Khan Academy         Mastery-based learning, skill tracking and support for personalising the learning pro-
                     cess.
Moodle               An open-source LMS: course and activity administration, role-based permissions, and
                     customisation and extension through plugins.


3. User Roles

The system must support at least three roles:
1. Learner: search for and enrol in courses, study the material, complete assignments, track progress
   and interact.
2. Instructor: create and manage courses, content, assignments and assessments, and monitor learners.
3. Administrator: manage users, courses, catalogues, reports and reported content.
Teams may add roles such as teaching assistant, moderator, training manager or parent where these suit
the problem.

4. Mandatory Core Functional Scope

4.1. Accounts and Authorisation
 • registration, login, logout and password recovery;
 • user profiles;
 • role-based authorisation and API protection;
 • account status management.

                                                         Page 4
CO3103 – Programming Integration Project, Semester 261                                 Online Learning Platform



4.2. Courses and Content
 • a course catalogue with search, filtering and pagination;
 • course detail pages covering the syllabus, the instructor and the prerequisites;
 • course structure organised into chapters/lessons;
 • support for at least two types of learning material — for example video, text, slides or attachments;
 • instructors can create, edit, publish and hide courses and content.

4.3. Enrolment and Learning
 • learners enrol in or leave a course;
 • view content in order and mark items as complete;
 • store and display learning progress;
 • resume study from the most recent position;
 • display courses in progress and courses completed.

4.4. Assignments and Assessment
 • quizzes and/or file-submission assignments;
 • time limits or submission deadlines;
 • automatic grading for quizzes;
 • instructors can view submissions, grade them and give feedback;
 • learners can view results and their attempt history.

4.5. Interaction and Notifications
 • one interaction channel, such as per-lesson discussion, Q&A or comments;
 • notifications for new content, upcoming deadlines or assessment results;
 • a mechanism for reporting inappropriate content.

4.6. Administration and Basic Reporting
 • user and course management;
 • basic metrics on enrolments, completion rates and learning outcomes;
 • an audit log of significant administrative actions.

5. Mandatory Advanced Component

  Each team must select and implement at least one of the seven directions below. A team may propose
  a different direction if the lecturer approves it in advance.

A component counts as “advanced” only when it is integrated into the main usage flow, has supporting
data or test scenarios, has evaluation criteria, and can be demonstrated live. A static interface or a single
isolated API call does not meet this requirement.



                                                         Page 5
CO3103 – Programming Integration Project, Semester 261                                 Online Learning Platform



5.1. Direction 1 – Grounded AI Teaching Assistant
 • a Q&A chatbot grounded in the course material using RAG or an equivalent technique;
 • answers must cite the relevant lesson or source;
 • handle out-of-scope questions, capture user feedback, and evaluate answer quality on a test set of ques-
   tions.

5.2. Direction 2 – Personalised Learning Paths and Recommendations
 • recommend courses or lessons based on the learner’s profile, goals, history and results;
 • give a brief explanation of why each recommendation was made;
 • compare against a baseline — such as most-popular or a hand-written rule — using an appropriate
   metric.

5.3. Direction 3 – Mastery-Based Adaptive Learning
 • model skills and prerequisite knowledge;
 • automatically adjust difficulty or the next lesson according to the learner’s results;
 • display a mastery map and demonstrate that the learning flow changes across at least two learner pro-
   files.

5.4. Direction 4 – Automated Assessment and Academic Integrity
 • a question bank that generates varied papers, automatic grading, or a secure execution environment for
   programming exercises;
 • support for rubrics, detection of unusual similarity, or examination-session controls;
 • the report must state the limitations and how mis-grading by the system is handled.

5.5. Direction 5 – Learning Analytics and Early Warning
 • a dashboard analysing learning behaviour and outcomes over time;
 • detect or predict learners at risk of falling behind;
 • provide explanations and intervention actions; evaluate on simulated data or on data you are permitted
   to use.

5.6. Direction 6 – Real-Time Collaborative Classroom
 • an online classroom, shared whiteboard, collaborative notes, breakout rooms or pair learning;
 • state synchronisation over WebSocket or an equivalent technology;
 • handle disconnection, rejoining and update conflicts to an appropriate degree.

5.7. Direction 7 – Learning Anywhere: PWA, Offline and Accessibility
 • installable as a Progressive Web App, storing lessons offline and synchronising when connectivity
   returns;
 • handle data conflicts or an offline operation queue;
 • support keyboard navigation, screen readers, captions or the relevant WCAG criteria, with accessibility
   testing.

                                                         Page 6
CO3103 – Programming Integration Project, Semester 261                               Online Learning Platform



6. Quality Requirements

 • a responsive interface, usable on both desktop and mobile;
 • a clearly designed API and database, with input validation and consistent error handling;
 • passwords must be hashed; sensitive information and secrets must never be committed to the repository;
 • automated tests for the important business logic, and end-to-end tests for at least one main flow;
 • sample data so that the lecturer can run and grade the product;
 • a Git repository with branches or pull requests, issues, and a contribution history for each member;
 • the product deployed to an environment the lecturer can access;
 • compliance with privacy and content copyright, and clear disclosure of any use of AI or third-party
   services.

7. Suggested Architecture

Teams choose their own technologies but must justify the choice. A reference architecture:
 • frontend: React, Vue, Angular or an equivalent framework;
 • backend: Node.js/NestJS, Java/Spring Boot, Python/Django/FastAPI, .NET or equivalent;
 • database: PostgreSQL, MySQL or another suitable DBMS;
 • storage: object storage for video and files;
 • infrastructure: Docker, CI/CD, a cloud deployment service;
 • observability: logging, error tracking and a few basic operational metrics.
Using this exact stack is not required. Teams must ensure the system can be reinstalled from the documen-
tation and does not depend on personal accounts the lecturer cannot access.

8. Milestones and Deliverables

8.1. Official Assessment Schedule
This document was updated on 3 September 2026, in week 36. Teams follow the milestones below.

Item                        Deadline                                       Main-content limit       Weight
Proposal                    23:59 Sun 20 September 2026 (end of week 38)       Max 4 pages            20%
Interim report              23:59 Sun 1 November 2026 (end of week 44)        Max 10 pages            30%
Final report                23:59 Sun 13 December 2026 (end of week 50)       Not specified           25%
Presentation and demo       Demo package ready by 23:59 Sun 13 Decem-         Not applicable          25%
                            ber 2026; presentation schedule announced
                            later
Total                                                                                                100%

The final report and demo together account for 50%: 25% for the final report and 25% for the presenta-
tion/demo.

  References and Appendices do not count towards the page limit and are not themselves limited in
  length. However, the lecturer is not obliged to read them when grading. Students must present all
  important information concisely within the permitted main-content pages.


                                                         Page 7
CO3103 – Programming Integration Project, Semester 261                               Online Learning Platform



The cover page, the table of contents and all main content — including tables and figures placed in the
body — do count towards the page limit. Content needed to satisfy the rubric must appear in the body;
References and Appendices must not be used to evade the page limit. Marks are decided primarily on the
main content and on evidence the lecturer can verify directly.

  Students must read this document in full and follow it exactly. Submitting in the wrong format or
  the wrong language, exceeding the page limit, omitting a mandatory component, using incorrect
  link/access settings, or failing to meet any other requirement will incur a mark deduction.


8.1.1. Language
 • English is mandatory. The proposal, interim report, final report, slides and all documents used for
   grading must be written in English.
 • The presentation, the live demo and the Q&A must be conducted in English.
 • The interface and the data used in the demo scenario must be consistent and clear enough to be presented
   in English.

8.1.2. Submission Cut-Off and Late Policy
 • “End of week” means 23:59 on the Sunday of that week, Vietnam time (UTC+7).
 • Any submission after 23:59 on Sunday is treated as late.
 • Each day late incurs a deduction of 20% of that item’s maximum mark. The mark after deduction
   is never below 0.
 • Example: the proposal is worth a maximum of 20 marks. One day late deducts 4 marks; two days late
   deducts 8 marks; five or more days late means the proposal scores 0.
 • Teams must verify their files and links before the deadline. A corrupt file, a link without access per-
   mission, or an unreachable deployment counts as an incomplete submission until it is fixed.

8.2. Proposal – 23:59 Sunday 20 September 2026, 20%
The proposal must be a standalone document of no more than 4 main-content pages, covering:
1. the problem, the context, the target users and the product’s value;
2. a brief analysis of related solutions and the intended point of difference;
3. the functional scope, the important user stories/use cases and the acceptance criteria;
4. the chosen advanced component, how it will be integrated, and the evaluation plan;
5. the intended architecture/technology stack, and the data model or a high-level diagram;
6. a plan covering weeks 39 to 50, the assignment of work to members, and the main risks.

8.3. Interim Report – 23:59 Sunday 1 November 2026, 30%
The interim report must be no more than 10 main-content pages, covering:
1. the agreed scope and any changes relative to the proposal;
2. the UI/UX design, the architecture, the data model and the main APIs;
3. completed features, evidence that they run, and the degree of integration;
4. the prototype or initial results of the advanced component;


                                                         Page 8
CO3103 – Programming Integration Project, Semester 261                                Online Learning Platform



5. testing, security, trial deployment and technical issues;
6. progress against plan, each member’s contribution, risks and the remaining plan.

8.4. Final Report and Demo – 23:59 Sunday 13 December 2026, 50%
By 23:59 on Sunday 13 December 2026, each team must submit the final report and have every resource in
place for the system to be presented and demonstrated. The specific presentation and live-demo schedule
will be announced later.
The final hand-over package comprises:
1. the final report, describing the final requirements, design, implementation, testing and results;
2. the source-code repository with installation/run instructions;
3. the deployed web application, with trial accounts for each role;
4. sample data, the API specification and the architecture/data-model documentation;
5. the test plan, test results and evidence of automated testing;
6. quantitative/qualitative evidence for the advanced component;
7. the slides and the demo script; a backup video is encouraged;
8. the weekly progress Google Doc, links to issues/pull requests/commits, and evidence of each member’s
   contribution.

9. Weekly Plan

Week      Main outcome
  38      Complete and submit the proposal (max 4 pages) before 23:59 Sunday 20 September 2026.
39–40     Finalise requirements, the UI/UX prototype, the architecture, the data model and the project foun-
          dation.
41–43     Implement and integrate the core features; build the advanced-component prototype.
  44      Verify the integrated build and submit the interim report (max 10 pages) before 23:59 Sunday 1
          November 2026.
45–46     Complete the core features and the advanced component; collect evaluation data.
  47      Functional, security, usability and performance testing, and bug fixing.
  48      Stabilise the deployment; finalise the evaluation, the report, the slides and the demo script.
  49      Rehearse the demo, resolve any remaining defects and finalise the demo package.
  50      Submit the final report and complete the product/demo before 23:59 Sunday 13 December 2026.
          The presentation/demo schedule will be announced later.

Teams must update the progress Google Doc every week for the whole duration of the project.

10. Team Formation and Registration

10.1. Size and Responsibilities
 • each team has 6–7 students;
 • each student belongs to exactly one team;
 • each team elects a leader as its point of contact;
 • the leader is responsible for consolidating information, sending the registration email and prompting
   progress updates; every member remains responsible for the accuracy of their own work.


                                                         Page 9
CO3103 – Programming Integration Project, Semester 261                                      Online Learning Platform



  Members of each team must make contact with one another and elect a leader as early as possible.
  Immediately after being elected, the leader sends the registration email and shares the progress Google
  Doc containing the full name, student ID and email address of every member.


10.2. Pre-Assigned Team List
The list below allocates every student on the current class rosters to a team. Teams 1–4 are unchanged
from the original allocation. Teams 5–21 were drawn at random from the remaining students, with each
team formed within a single class roster so that its members share a class. Every team has 6 or 7 students,
apart from Team 15 (see the note below the table). Each team elects its own leader and records this in the
Google Doc before the team-formation deadline.

     Team No. Full name                       Student ID Class Email
       1      1   HẦU THÁI TÚ                  2353288      A02    tu.hauthaitu@hcmut.edu.vn
       1      2   Nguyễn Quang Vị              2453439      A02    vi.nguyenstudy@hcmut.edu.vn
       1      3   Hàng Thái An                 2452004      CC03   an.hangthaianhang@hcmut.edu.vn
       1      4   Võ Thanh An†                 2452030       –     an.vo1410@hcmut.edu.vn
       1      5   Kiều Gia Bảo                 2410236      CC03   bao.kieugia0905@hcmut.edu.vn
       1      6   Trương Lê Bảo†               2452147       –     bao.truong05122006@hcmut.edu.vn
       2      1   TRẦN QUỐC BẢO                2352110      CC03   bao.tran2352110@hcmut.edu.vn
       2      2   Hồ Phương Duy                2452195      CC03   duy.hoar2006@hcmut.edu.vn
       2      3   Võ Hoàng Phúc Duy†           2452214       –     duy.vok242452214@hcmut.edu.vn
       2      4   Lê Huy Hoàng                 2452355      CC03   hoang.lelhh1906lx@hcmut.edu.vn
       2      5   LƯƠNG THẾ HOÀNG              2453471      CC03   hoang.luong010206@hcmut.edu.vn
       2      6   Vũ Lê Hoàng†                 2452365       –     hoang.vuhcmut@hcmut.edu.vn
       3      1   HỒ QUỐC HUY                  2352379      CC03   huy.hoiconic05@hcmut.edu.vn
       3      2   PHẠM QUANG HUY               2352405      CC03   huy.pham06012005@hcmut.edu.vn
       3      3   NGUYỄN GIA HÙNG              2352422      CC03   hung.nguyenfreddybear@hcmut.edu.vn
       3      4   Đào Nguyên Hưng              2452421      CC03   hung.daonguyenhung@hcmut.edu.vn
       3      5   Lê Đoàn Hoàn Hải             2452307      CC03   hai.le282452307@hcmut.edu.vn
       3      6   Phạm Hoàng Hải               2450007      CC03   hai.pham1508@hcmut.edu.vn
       4      1   Asami Keisuke†               2660052       –     keisuke.asamiexc@hcmut.edu.vn
       4      2   Đinh Phúc Khang              2452453      CC03   khang.dinh06197574@hcmut.edu.vn
       4      3   BÙI VŨ THIÊN ĐĂNG            2252151      CC03   dang.buivuthien@hcmut.edu.vn
       4      4   Nguyễn Tất Đạt               2452248      CC03   dat.nguyen2452248@hcmut.edu.vn
       4      5   Võ Lưu Thiên Phú             2412693      TN02   phu.vo2802@hcmut.edu.vn
       4      6   Nguyễn Hồng Phúc             2412732      TN02   phuc.nguyen2412732@hcmut.edu.vn
       4      7   Trần Minh Thuật              2413393      TN02   thuat.tranminhpy@hcmut.edu.vn
       5      1   Trần Huy Khang               2452477      CC03   khang.tran2452477@hcmut.edu.vn
       5      2   Huỳnh Đức Phát               2452940      CC03   phat.huynhhellofunny11@hcmut.edu.vn
       5      3   ĐỖ TUẤN KHÔI                 2352605      CC03   khoi.domk030105@hcmut.edu.vn
       5      4   Trần Hoàng Phúc              2453013      CC03   phuc.tranhoang0908@hcmut.edu.vn
       5      5   Lê Trọng Phúc                2452995      CC03   phuc.le191006@hcmut.edu.vn
       5      6   Hoàng Văn Mệnh               2452730      CC03   menh.hoang@hcmut.edu.vn
       5      7   Nguyễn Việt Khoa             2452549      CC03   khoa.nguyenvietk24cse@hcmut.edu.vn
       6      1   Đặng Hồng Phúc               2450032      CC03   phuc.danghong140306@hcmut.edu.vn
       6      2   Trần Lê Tuấn Khải            2452517      CC03   khai.tran266@hcmut.edu.vn
       6      3   Nguyễn Đăng Khoa             2452543      CC03   khoa.nguyen11@hcmut.edu.vn
       6      4   Nguyễn Đình Đăng Khoa        2411630      CC03   khoa.nguyen0809@hcmut.edu.vn
       6      5   Phạm Vũ Khôi Nguyên          2412372      CC03   nguyen.pham1403@hcmut.edu.vn
       6      6   Trần Bảo Ngọc                2452838      CC03   ngoc.tran2101@hcmut.edu.vn
       7      1   Đinh Nam Khánh               2452491      CC03   khanh.dinhalex@hcmut.edu.vn
       7      2   Nguyễn Đình Phúc             2452999      CC03   phuc.nguyen2122006@hcmut.edu.vn
       7      3   Huỳnh Tuấn Kiệt              2452627      CC03   kiet.huynh0807kite@hcmut.edu.vn
       7      4   Hoàng Lâm Đăng Khôi          2452575      CC03   khoi.hoang862006@hcmut.edu.vn
       7      5   Nguyễn Trung Kiên            2452615      CC03   kien.nguyencse@hcmut.edu.vn


                                                         Page 10
CO3103 – Programming Integration Project, Semester 261                                       Online Learning Platform



     Team No. Full name                       Student ID Class Email
       7      6   Lục Khánh Phát               2452942      CC03 phat.luclkp2006@hcmut.edu.vn
       8      1   Thái Khang                   2452475      CC03   khang.thai06@hcmut.edu.vn
       8      2   Nguyễn Trần Tuấn Khải        2452514      CC03   khai.nguyenmmct142@hcmut.edu.vn
       8      3   Lê Minh Nhật                 2452891      CC03   nhat.leminh0110@hcmut.edu.vn
       8      4   Nguyễn Sơn Bách              2452117      CC03   bach.nguyen263@hcmut.edu.vn
       8      5   Hà Công Minh                 2452740      CC03   minh.haengineer@hcmut.edu.vn
       8      6   Nguyễn Công Minh             2452750      CC03   minh.nguyencong1711@hcmut.edu.vn
       9      1   Nguyễn Anh Tài               2413035       L06   tai.nguyenanh1906@hcmut.edu.vn
       9      2   Mai Đức Trường Sơn           2413010       L06   son.mailightning@hcmut.edu.vn
       9      3   Nguyễn Chí Thành             2413163       L06   thanh.nguyenchihcmut@hcmut.edu.vn
       9      4   Nguyễn Lê Bảo Duy            2134006       A02   duy.nguyencse94@hcmut.edu.vn
       9      5   Lê Hoàng Minh Trí            2453307       A02   tri.lelhmtri@hcmut.edu.vn
       9      6   Lê Phan Quốc Thắng           2413232       L06   thang.le12012006@hcmut.edu.vn
       9      7   Bùi Minh Tân                 2413077       L06   tan.buikhmt0429@hcmut.edu.vn
       10     1   Nguyễn Đình Nhã              2533036       A02   nha.nguyen1402@hcmut.edu.vn
       10     2   Lâm Chí Nguyên               2412332       L06   nguyen.lam1707@hcmut.edu.vn
       10     3   Nguyễn Huỳnh Tấn Phát        2412585       L06   phat.nguyen2412585@hcmut.edu.vn
       10     4   Bùi Quang Đức                2410796       L06   duc.bui2007@hcmut.edu.vn
       10     5   Đặng Bá Phú                  2412657       L06   phu.dang2006@hcmut.edu.vn
       10     6   Nguyễn Anh Quân              2412898       L06   quan.nguyenanh126@hcmut.edu.vn
       10     7   Nguyễn Hoàng Phúc Tâm        2413063       L06   tam.nguyen09022006@hcmut.edu.vn
       11     1   Huỳnh Duy Khang              2452456       A02   khang.huynh6021z@hcmut.edu.vn
       11     2   Lê Anh Thư                   2453217       A02   thu.leanhthu@hcmut.edu.vn
       11     3   Đặng Thị Hoàng Mai           2411997       L06   mai.dang2411997@hcmut.edu.vn
       11     4   Huỳnh Trần Hữu Nghĩa         2412259       L06   nghia.huynhth183@hcmut.edu.vn
       11     5   Bùi Trung Hải                2410893       L06   hai.bui73@hcmut.edu.vn
       11     6   HUỲNH HOÀNG TUẤN             2353267       A02   tuan.huynhhoang2005@hcmut.edu.vn
       11     7   Nguyễn Bùi Nguyễn            2412386       L06   nguyen.nguyenbui@hcmut.edu.vn
       12     1   Nguyễn Hoàng Nam             2452790       A02   nam.nguyenyunkar1807@hcmut.edu.vn
       12     2   Nguyễn Hồng Thiện Nhân       2412418       L06   nhan.nguyenbk24@hcmut.edu.vn
       12     3   Nguyễn Huỳnh Đức             2036118       L06   duc.nguyen9501@hcmut.edu.vn
       12     4   Huỳnh Minh Hiển              2520003       L06   hien.huynhgse@hcmut.edu.vn
       12     5   Khâu Phương Nghi             2412246       L06   nghi.khaukpn@hcmut.edu.vn
       12     6   Nguyễn Lê Phú Tài            2413039       L06   tai.nguyen10shi@hcmut.edu.vn
       12     7   Phạm Tiến Đạt                2410724       L06   dat.phamkhmtk24@hcmut.edu.vn
       13     1   Đào Nguyên Hạnh              2410920       L06   hanh.daonguyen@hcmut.edu.vn
       13     2   Đặng Hoàng Quý Nhân          2412401       L06   nhan.dangcs06@hcmut.edu.vn
       13     3   Nguyễn Hữu Tây               2413095       L06   tay.nguyen2413095@hcmut.edu.vn
       13     4   Ngô Võ Gia Minh              2412069       L06   minh.ngovogia2006@hcmut.edu.vn
       13     5   Chiêm Tuyền Minh             2412032       L06   minh.chiem345@hcmut.edu.vn
       13     6   CHU HUỲNH PHÚC               2352927       A02   phuc.chuhyn8254139@hcmut.edu.vn
       13     7   Nguyễn Tuấn Kiệt             2452636       A02   kiet.nguyenrossoneri@hcmut.edu.vn
       14     1   Lê Minh Lượng                1933020       L06   luong.leluonggao69@hcmut.edu.vn
       14     2   Ngô Minh Quân                2453071       A02   quan.ngoqria0786@hcmut.edu.vn
       14     3   Nguyễn Hoàng Minh            2412086       L06   minh.nguyenhoang@hcmut.edu.vn
       14     4   Hoàng Ngọc Hiển              2411028       L06   hien.hoang120206@hcmut.edu.vn
       14     5   NGUYỄN LÊ DUY                2352186       A02   duy.nguyenduy128@hcmut.edu.vn
       14     6   Nguyễn Hoàng Phúc            2453001       A02   phuc.nguyenphuckhmt61@hcmut.edu.vn
       14     7   Thái Lê Gia Bảo              2452142       A02   bao.thai2452142@hcmut.edu.vn
       15     1   Võ Văn Gia Khánh             2411549      TN02 khanh.vo021206@hcmut.edu.vn
       15     2   Nguyễn An Nhật               2412473      TN02 nhat.nguyenan1506@hcmut.edu.vn
       15     3   Phạm Văn Hên                 2410968      TN02 hen.pham2410968@hcmut.edu.vn
       16     1   Trần Đình Đô                 2410787       L06   do.trankhmt2024@hcmut.edu.vn
       16     2   PHAN LÊ THIÊN                2313221       L06   thien.phan2411@hcmut.edu.vn
       16     3   Phạm Nguyễn Thảo Ly          2411995       L06   ly.pham241195@hcmut.edu.vn
       16     4   PHẠM THÁI DUY BẢO            2352107       A02   bao.pham0716@hcmut.edu.vn
       16     5   Nguyễn Ngọc Duy              2452204       A02   duy.nguyenngock24@hcmut.edu.vn


                                                         Page 11
CO3103 – Programming Integration Project, Semester 261                                      Online Learning Platform



     Team No. Full name                       Student ID Class Email
       16     6   Trần Nhân Sâm                2453120       A02   sam.transamchan@hcmut.edu.vn
       17     1   Võ Phúc Khang                2452482       A02   khang.vothichanpho@hcmut.edu.vn
       17     2   Nguyễn Trung Hiếu            2411002       L06   hieu.nguyen02112006@hcmut.edu.vn
       17     3   Nguyễn Trường Giang          2410841       L06   giang.nguyenvt0610@hcmut.edu.vn
       17     4   Trần Duy Phước Nghĩa         2412275       L06   nghia.tranhcmut2506@hcmut.edu.vn
       17     5   Trương Vỹ Kiệt               2520005       L06   kiet.truong145@hcmut.edu.vn
       17     6   VŨ MINH PHÚ                  2352925       A02   phu.vu150506@hcmut.edu.vn
       18     1   Phan Quốc Đạt                2410717       L06   dat.phan717cs@hcmut.edu.vn
       18     2   Phan Hải Phong               2412650       L06   phong.phanhai06@hcmut.edu.vn
       18     3   Quách Hoàng Linh             2411895       L06   linh.quachhoang@hcmut.edu.vn
       18     4   Nguyễn Thiên Phước           2453045       A02   phuoc.nguyenthien1410@hcmut.edu.vn
       18     5   TRẦN MINH TÂM                2213038       L06   tam.tranminhtam@hcmut.edu.vn
       18     6   Trương Hiển Minh             2452771       A02   minh.truonghien@hcmut.edu.vn
       19     1   Nguyễn Thành Nam             2412187       L06   nam.nguyen48677@hcmut.edu.vn
       19     2   PHẠM MINH NHẬT               2312484       L06   nhat.phamminh2312@hcmut.edu.vn
       19     3   Trần Vũ Đức Hoàng            1711419       A02   hoang.tranbknetid@hcmut.edu.vn
       19     4   Trần Tiến Thành              2413181       L06   thanh.tran311@hcmut.edu.vn
       19     5   Võ Thái Kiệt                 2411807       L06   kiet.vok24cs@hcmut.edu.vn
       19     6   Võ Tấn Phát                  2412616       L06   phat.votan242006@hcmut.edu.vn
       20     1   NGUYỄN THỊ THÚY              2310904       L06   hang.nguyen1809@hcmut.edu.vn
                  HẰNG
       20     2   Trần Thị Yến Phương          2412794       L06   phuong.tran1284@hcmut.edu.vn
       20     3   VÕ THANH NAM                 2352789       A02   nam.vothanhnam22@hcmut.edu.vn
       20     4   Trần Ngọc Phương Mai         2452720       A02   mai.tranngocphuongmai2452720@hcmut.edu.vn
       20     5   Phạm Minh Kỳ                 2411820       L06   ky.pham270106@hcmut.edu.vn
       20     6   Trần Mai Hạnh Như            2452927       A02   nhu.tranakaine38@hcmut.edu.vn
       21     1   Võ Nguyễn Đăng Khoa          2452571       A02   khoa.vo2452571@hcmut.edu.vn
       21     2   Võ Hoàng Tuấn Kiệt           2411806       L06   kiet.vo2186@hcmut.edu.vn
       21     3   TẠ NHẬT LINH                 2252432       A02   linh.tanhat2004@hcmut.edu.vn
       21     4   Văn Thị Như Quỳnh            2412984       L06   quynh.vannhuquynh06@hcmut.edu.vn
       21     5   Nguyễn Võ Hoàng Sơn          2453128       A02   son.nguyenhoang24@hcmut.edu.vn
       21     6   Trần Mậu Giàu                2410851       L06   giau.tranmau@hcmut.edu.vn


† This student appears in the original allocation but was not found on any current class roster.Their
enrolment must be confirmed before the team-formation deadline.
Team 15 has only 3 students: the TN02 roster (course 148770) contains six students in total, three of
whom are already in Team 4. This team requires the lecturer’s approval or a correspondingly reduced
project scope.

10.3. Team-Change Rules
 • a student who wants to change team must take the initiative to find a student in another team who agrees
   to swap places directly;
 • a change is considered only when both parties agree. The two students involved and both team leaders
   must confirm their agreement in the same email thread;
 • one of the two students emails the lecturer requesting the swap, copying (CC) the other student and
   both team leaders. The email must state the full name, student ID, current team and requested team of
   both students;
 • a swap must preserve the required team sizes and takes effect only after the lecturer confirms it. Do
   not change names in the Google Doc before receiving confirmation;
 • swap requests should be sent as early as possible so as not to disrupt leader elections, work allocation
   and proposal preparation. All changes require the lecturer’s approval.


                                                         Page 12
CO3103 – Programming Integration Project, Semester 261                                Online Learning Platform



Subject line for a swap request:

                         DATH-261-SWAP-<Student 1 name>-<Student 2 name>

Email template:

  Dear Lecturer,
  We would like to request the following group swap:
  Student 1: <Full name>, <Student ID>, from Group <A> to Group <B>
  Student 2: <Full name>, <Student ID>, from Group <B> to Group <A>
  Both students agree to this swap. The leaders of Group <A> and Group
  <B> are copied on this email and confirm their agreement by replying
  to this email thread.
  We understand that the swap is effective only after the lecturer's
  confirmation.
  Kind regards,
  <Full name>
  <Student ID>



10.4. Registration Email
Immediately after the team is formed, the leader emails the lecturer with the subject line:

                                       DATH-261-<Team leader name>

For example: DATH-261-Nguyen Van An.
The email must contain at least:
 • the team name;
 • the full name and student ID of the team leader;
 • the list of members;
 • the intended context/type of online learning platform;
 • the intended advanced direction;
 • a link to the progress Google Doc, shared with the correct permissions.
The Google Doc is a live document that must be updated weekly. The leader should include the Google
Doc link in the email or attach it via Google Drive — do not send an exported PDF/DOCX in place of the
shared link. The document must grant the lecturer access, and team members must have edit permission.

10.5. Team Registration Email Template

  Dear Lecturer,
  My name is <Full name of group leader>, Student ID <Student ID>, and I am
  the representative of <Group name>. Our group consists of <6/7>
  members and would like to register an online learning web application
  project with the following direction: <Brief description>.
  Proposed advanced component: <Name and expected outcome>.
  Weekly progress Google Doc: <Google Doc URL>
  The Google Doc contains the full name, student ID, and email address
  of every member. We have granted the lecturer access and all members
  editing permission.
  Kind regards,
  <Full name of group leader>
  <Student ID>
  <Email>
  <Group name>




                                                         Page 13
CO3103 – Programming Integration Project, Semester 261                                Online Learning Platform



10.6. Interim Report Submission Email Template
The team leader submits the interim report by email, in English. Subject line:
                         DATH-261-INTERIM-<Team name>-<Team leader name>

The email must attach the interim report as a PDF and provide the links the lecturer needs in order to check
progress.

  Dear Lecturer,
  On behalf of <Group name>, I am submitting our interim report for
  CO3103 - Programming Integration Project (Software Engineering),
  Semester 261.
  Group leader: <Full name>
  Student ID: <Student ID>
  Project title: <Project title>
  Submission package:
  1. Interim report (PDF, maximum 10 main-content pages): <Attached/Link>
  2. Deployed prototype: <URL>
  3. Source-code repository: <URL and branch/tag>
  4. Weekly progress Google Doc: <URL>
  5. Supporting demo or evidence, if any: <URL>
  Current advanced component: <Name and one-sentence status>
  Current overall status: <On schedule/At risk/Delayed, with brief reason>
  We have verified that all files and links are accessible to the lecturer.
  Kind regards,
  <Full name of group leader>
  <Student ID>
  <Email>
  <Group name>



10.7. Final Report and Demo Package Submission Email Template
The team leader submits the final report and the demo resources by email, in English. Subject line:
                          DATH-261-FINAL-<Team name>-<Team leader name>

  Dear Lecturer,
  On behalf of <Group name>, I am submitting our final report and
  demo-ready project package for CO3103 - Programming Integration
  Project (Software Engineering), Semester 261.
  Group leader: <Full name>
  Student ID: <Student ID>
  Project title: <Project title>
  Advanced component: <Advanced component name>
  Final submission package:
  1. Final report (PDF): <Attached/Link>
  2. Production deployment: <URL>
  3. Demo accounts and credentials: <Secure location/instructions>
  4. Source-code repository: <URL and final release/tag/commit>
  5. Installation and reproduction guide: <URL>
  6. Test report and advanced-component evaluation: <URL>
  7. Presentation slides: <Attached/Link>
  8. Backup demo video, if available: <URL>
  9. Weekly progress Google Doc: <URL>
  We confirm that the submitted system and demo package are ready for
  the presentation and live-demo schedule to be announced later. We have
  verified that all files, accounts, and links are accessible to the lecturer.
  Kind regards,
  <Full name of group leader>
  <Student ID>
  <Email>
  <Group name>



                                                         Page 14
CO3103 – Programming Integration Project, Semester 261                                  Online Learning Platform



11. Weekly Progress Google Doc Template

Suggested document name: DATH-261-<Team name>-Weekly-progress.

11.1. Team Information

No.    Full name                                          Student ID   Email   Role / responsibility
 1                                                                             Team leader
 2
 3
 4
 5
 6
 7                                                                             Delete this row for a team of 6

11.2. Project Information
 • Project title:
 • Target users:
 • Problem to be solved:
 • MVP scope:
 • Chosen advanced component:
 • Source-code repository:
 • Deployment:
 • Task-management board:

11.3. Weekly Progress Log
Create one entry per week, containing:
1. the goals for the week;
2. work completed, per member, with issues and evidence;
3. work not completed, and why;
4. issues/risks that need support;
5. the plan for the following week;
6. significant team decisions.
Links to issues, pull requests, commits, designs or demo videos should be used as evidence. Do not simply
record an overall percentage without naming concrete deliverables.

12. Assessment Rubrics

12.1. How the Rubrics Are Applied
Each criterion is graded on how fully it is met and on the actual evidence provided:
 • Excellent – 100% of the criterion’s marks: complete, correct, consistent, with convincing evidence
   and clear analysis;



                                                         Page 15
CO3103 – Programming Integration Project, Semester 261                                   Online Learning Platform



 • Good – approximately 75%: most requirements met, with minor shortcomings that do not affect the
   main objectives;
 • Partially met – approximately 50%: results exist but lack depth, are poorly integrated, or the evidence
   is weak;
 • Not met – 0–25%: missing, incorrect, not working, unevidenced or unexplainable.
The lecturer may award marks between these levels according to actual quality. A team’s mark may be
adjusted for individual contribution as evidenced by the Google Doc, issues, pull requests, commits, the
report and the Q&A.

12.2. Proposal Rubric – 20%

Criterion                       Marks      Requirement for full marks
Problem, context and                3      States the real need, the target users, the pain points and the prod-
users                                      uct’s value.
Requirements and scope              4      Features, user stories/use cases, MVP, out-of-scope items and ac-
                                           ceptance criteria are clear and feasible.
Solution and high-level             4      Main flows, architecture, data, UI/UX and technology choices are
design                                     sound and consistent.
Advanced component                  4      A specific direction chosen, with its difficulty, integration ap-
                                           proach, data/experiments and evaluation metrics explained.
Implementation plan                 3      Milestones for weeks 39–50, work allocated across all 6–7 mem-
                                           bers, dependencies, risks and mitigations.
Proposal quality                    2      Clearly presented, within the 4-page limit, legible figures/tables,
                                           correct citations and no significant contradictions.
Total                              20

12.3. Interim Report Rubric – 30%

Criterion                       Marks      Requirement for full marks
Progress against the                5      A transparent goal-versus-result comparison, explanation of
proposal                                   changes, and a recovery plan where behind schedule.
Requirements, UI/UX and             5      Design updated to reflect development; flows, data, APIs and sys-
architecture                               tem components are consistent.
Core feature                        7      The important flows run and are integrated; evidence from the de-
implementation                             ployment, screenshots or video, and sample data.
Advanced component                  5      An integrated prototype or initial results; a clear evaluation plan,
                                           data, baseline/metrics and risks.
Software engineering and            5      Git/issue management, testing, error handling, basic security, and
quality                                    appropriate CI/CD or trial deployment.
Report and team                     3      Clear report within the 10-page limit, with evidence and a concrete
contribution                               account of each member’s work.
Total                              30




                                                         Page 16
CO3103 – Programming Integration Project, Semester 261                                    Online Learning Platform



12.4. Final Report Rubric – 25%

Criterion                       Marks      Requirement for full marks
Problem, requirements               4      Describes the final requirements and traces them through design,
and traceability                           implementation and testing.
Architecture and                    5      Explains technical decisions, the data model, APIs, the main com-
implementation                             ponents and trade-offs, with evidence from the system.
Advanced component                  5      Describes the method, integration, data/experiments, baseline or
                                           control, and a well-grounded analysis of results.
Testing and system quality          5      A test strategy, test cases/results, automated testing, security, per-
                                           formance/reliability and defect analysis.
Deployment and                      3      An accessible deployment; instructions, configuration, sample data
reproducibility                            and demo accounts that allow the system to be re-run.
Report quality and project          3      Clear writing, correct figures/tables and citations; states limita-
reflection                                 tions, lessons learned, individual contributions and future work.
Total                              25

12.5. Presentation and Demo Rubric – 25%

Criterion                       Marks      Requirement for full marks
Demo of the core flows              6      A live, uninterrupted demonstration of the main learner, instructor
                                           and administrator flows using realistic data.
Demo of the advanced                6      The advanced feature works within the product; the team demon-
component                                  strates a meaningful scenario and explains the evaluation results.
Stability and user                  4      The system responds well, the UI/UX is consistent, errors are han-
experience                                 dled sensibly, and there are no serious defects in the main scenario.
Presentation quality                3      Clear structure, within the allotted time, focused on problem–
                                           solution–evidence rather than a list of features.
Technical understanding             4      Able to explain the architecture, the code, the data, the technical
and Q&A                                    choices, the limitations and the team’s decisions.
Member participation                2      Roles sensibly divided; members understand their own work and
                                           contribution.
Total                              25


13. Minimum Acceptance Criteria

The project is considered eligible for the final demo when:
 • the deployment is reachable and can be logged into with the trial accounts;
 • the main flows for learner, instructor and administrator are complete;
 • the advanced component works within the product, with a demo scenario and evaluation results;
 • there are no serious defects causing data loss or bypassing authorisation;
 • the source code, sample data, installation documentation and test report have been handed over;
 • the progress Google Doc is kept up to date and reflects each member’s contribution.




                                                         Page 17
