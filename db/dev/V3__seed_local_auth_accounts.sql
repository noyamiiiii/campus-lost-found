-- Local development only: attach login identifiers to the existing demo users.

UPDATE `sys_user`
SET `student_no` = '20260001',
    `real_name` = '林知夏',
    `phone` = '13800000001',
    `dept` = '计算机学院',
    `phone_verified_at` = CURRENT_TIMESTAMP(3)
WHERE `public_id` = 'usr_demo_0001';

UPDATE `sys_user`
SET `student_no` = '20260002',
    `real_name` = '周屿',
    `phone` = '13800000002',
    `dept` = '信息工程学院',
    `phone_verified_at` = CURRENT_TIMESTAMP(3)
WHERE `public_id` = 'usr_demo_0002';
