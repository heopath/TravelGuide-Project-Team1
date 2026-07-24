-- Synthetic local data only. Replace the password placeholder before login testing.
USE all_my_trips;
SET NAMES utf8mb4;
START TRANSACTION;
INSERT INTO travel_styles (travel_style_id,code,name,description,sort_order) VALUES
(1,'SIGHTSEEING','관광','대표 명소 중심',1),(2,'FOOD','맛집','지역 음식 중심',2),(3,'HEALING','힐링','휴식 중심',3),(4,'ACTIVITY','액티비티','체험 중심',4),(5,'CAFE','카페','카페 중심',5);
DELIMITER $$
CREATE PROCEDURE seed_local_users()
BEGIN
 DECLARE i INT DEFAULT 1; DECLARE j INT;
 WHILE i<=10 DO
  INSERT INTO users(user_id,email,password_hash,nickname,role,status) VALUES(i,CONCAT('local-user',i,'@example.invalid'),'REPLACE_WITH_TEAM_BCRYPT_HASH',CONCAT('테스트사용자',i),IF(i>=9,'ADMIN','USER'),CASE WHEN i=7 THEN 'SUSPENDED' WHEN i=8 THEN 'WITHDRAWN' ELSE 'ACTIVE' END);
  SET j=0; WHILE j<3 DO INSERT INTO user_preferences(user_id,travel_style_id,preference_score,source) VALUES(i,MOD(i+j-1,5)+1,55+MOD(i*7+j*11,46),IF(j=2,'INFERRED','EXPLICIT')); SET j=j+1; END WHILE;
  SET i=i+1;
 END WHILE;
END$$
DELIMITER ;
CALL seed_local_users(); DROP PROCEDURE seed_local_users;
COMMIT;
