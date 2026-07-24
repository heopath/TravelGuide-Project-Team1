USE all_my_trips; SET NAMES utf8mb4; START TRANSACTION;
DELIMITER $$
CREATE PROCEDURE seed_local_trips()
BEGIN
 DECLARE tid INT DEFAULT 1; DECLARE d INT; DECLARE s INT; DECLARE day_id INT DEFAULT 1; DECLARE item_id INT DEFAULT 1; DECLARE duration_days INT; DECLARE start_day DATE; DECLARE region_name VARCHAR(100); DECLARE trip_status VARCHAR(20); DECLARE src VARCHAR(20);
 WHILE tid<=15 DO
  SET duration_days=IF(tid<=10,3,2); SET start_day=DATE_ADD('2026-08-01',INTERVAL (tid-1)*5 DAY); SET region_name=ELT(MOD(tid-1,8)+1,'서울','부산','제주','강릉','경주','전주','여수','인천'); SET trip_status=CASE WHEN tid<=4 THEN 'DRAFT' WHEN tid<=10 THEN 'CONFIRMED' WHEN tid<=14 THEN 'COMPLETED' ELSE 'CANCELLED' END; SET src=IF(MOD(tid,2)=0,'AI','MANUAL');
  INSERT INTO trips(trip_id,user_id,title,destination_name,start_date,end_date,companion_type,companion_count,purpose,budget_amount,currency_code,transport_preference,food_preference,pace,accommodation_style,status,source) VALUES(tid,MOD(tid-1,8)+1,CONCAT(region_name,' 여행 ',tid),region_name,start_day,DATE_ADD(start_day,INTERVAL duration_days-1 DAY),ELT(MOD(tid-1,5)+1,'SOLO','FRIENDS','COUPLE','FAMILY','GROUP'),MOD(tid,4)+1,'지역 여행',300000+tid*50000,'KRW','대중교통','지역 음식',ELT(MOD(tid,3)+1,'RELAXED','NORMAL','PACKED'),'외부 예약 숙소',trip_status,src);
  INSERT INTO trip_travel_styles VALUES(tid,MOD(tid-1,5)+1,1,CURRENT_TIMESTAMP(6)),(tid,MOD(tid,5)+1,2,CURRENT_TIMESTAMP(6));
  SET d=1; WHILE d<=duration_days DO INSERT INTO trip_days(trip_day_id,trip_id,day_number,trip_date,title,memo) VALUES(day_id,tid,d,DATE_ADD(start_day,INTERVAL d-1 DAY),CONCAT(region_name,' ',d,'일차'),'합성 일정'); SET s=1; WHILE s<=4 DO INSERT INTO itinerary_items(itinerary_item_id,trip_day_id,place_id,item_type,title,start_time,end_time,sort_order,memo,estimated_cost,currency_code,source) VALUES(item_id,day_id,MOD(tid-1,8)+1+8*MOD(d*4+s,12),ELT(s,'PLACE','MEAL','ACTIVITY','PLACE'),CONCAT(region_name,' 일정 ',d,'-',s),MAKETIME(7+s*2,0,0),MAKETIME(8+s*2,30,0),s,'합성 일정 항목',10000+s*8000,'KRW',src); SET item_id=item_id+1; SET s=s+1; END WHILE; SET day_id=day_id+1; SET d=d+1; END WHILE;
  SET tid=tid+1;
 END WHILE;
END$$
DELIMITER ;
CALL seed_local_trips(); DROP PROCEDURE seed_local_trips; COMMIT;
