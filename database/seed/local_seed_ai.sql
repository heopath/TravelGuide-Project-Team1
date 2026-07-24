USE all_my_trips; SET NAMES utf8mb4; START TRANSACTION;
DELIMITER $$
CREATE PROCEDURE seed_local_ai()
BEGIN
 DECLARE i INT DEFAULT 1; DECLARE st VARCHAR(20); DECLARE et VARCHAR(30);
 WHILE i<=20 DO SET st=ELT(MOD(i-1,6)+1,'SUCCEEDED','SUCCEEDED','SUCCEEDED','FAILED','PENDING','PROCESSING'); INSERT INTO ai_generation_requests(ai_generation_request_id,user_id,trip_id,request_type,provider,model_name,prompt_version,input_payload,output_payload,status,error_message,input_tokens,output_tokens,requested_at,completed_at) VALUES(i,MOD(i-1,8)+1,IF(i<=15,i,NULL),ELT(MOD(i-1,3)+1,'CREATE_ITINERARY','OPTIMIZE_ROUTE','CHAT'),'MOCK','mock-travel-model','v1',JSON_OBJECT('requestNo',i,'synthetic',TRUE),IF(st='SUCCEEDED',JSON_OBJECT('summary',CONCAT('합성 응답 ',i)),NULL),st,IF(st='FAILED','모의 실패',NULL),100+i,IF(st='SUCCEEDED',50+i,NULL),DATE_ADD('2026-07-24 09:00:00',INTERVAL i MINUTE),IF(st IN('SUCCEEDED','FAILED'),DATE_ADD('2026-07-24 10:00:00',INTERVAL i MINUTE),NULL)); SET i=i+1; END WHILE;
 SET i=1; WHILE i<=120 DO SET et=ELT(MOD(i-1,6)+1,'IMPRESSION','IMPRESSION','CLICK','FAVORITE','ADD_TO_TRIP','DISMISS'); INSERT INTO recommendation_events(user_id,place_id,trip_id,ai_generation_request_id,event_type,session_id,metadata,occurred_at) VALUES(MOD(i-1,8)+1,MOD(i*11-1,100)+1,MOD(i-1,15)+1,MOD(i-1,20)+1,et,CONCAT('seed-session-',FLOOR((i-1)/6)+1),JSON_OBJECT('rank',MOD(i-1,10)+1,'score',0.55+MOD(i,40)/100),DATE_ADD('2026-07-24 12:00:00',INTERVAL i SECOND)); SET i=i+1; END WHILE;
END$$
DELIMITER ;
CALL seed_local_ai(); DROP PROCEDURE seed_local_ai; COMMIT;
