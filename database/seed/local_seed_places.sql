USE all_my_trips; SET NAMES utf8mb4; START TRANSACTION;
DELIMITER $$
CREATE PROCEDURE seed_local_places()
BEGIN
 DECLARE i INT DEFAULT 1; DECLARE cat VARCHAR(30); DECLARE region_name VARCHAR(100); DECLARE s1 INT; DECLARE s2 INT;
 WHILE i<=100 DO
  SET cat=CASE WHEN i<=25 THEN 'ATTRACTION' WHEN i<=45 THEN 'RESTAURANT' WHEN i<=60 THEN 'CAFE' WHEN i<=75 THEN 'ACCOMMODATION' WHEN i<=80 THEN 'FESTIVAL' WHEN i<=90 THEN 'ACTIVITY' ELSE 'TRANSPORT' END;
  SET region_name=ELT(MOD(i-1,8)+1,'서울','부산','제주','강릉','경주','전주','여수','인천');
  INSERT INTO places(place_id,external_provider,external_place_id,category,name,country_code,region,city,address,latitude,longitude,description,phone,website_url,average_rating,is_active) VALUES(i,'LOCAL_SEED',CONCAT('PLACE-',LPAD(i,3,'0')),cat,CONCAT(region_name,' ',cat,' ',LPAD(i,3,'0')),'KR',region_name,CONCAT(region_name,'시'),CONCAT(region_name,' 테스트로 ',i),33.2+MOD(i,40)*0.12,126.1+MOD(i,50)*0.11,CONCAT(region_name,' 합성 장소'),'02-0000-0000',IF(cat='ACCOMMODATION',CONCAT('https://example.invalid/accommodations/',i),CONCAT('https://example.invalid/places/',i)),3.2+MOD(i,18)*0.1,TRUE);
  INSERT INTO place_images(place_id,image_url,alt_text,sort_order,is_primary) VALUES(i,CONCAT('https://picsum.photos/seed/all-my-trips-place-',i,'-1/1200/800'),CONCAT(region_name,' 대표 이미지'),1,TRUE),(i,CONCAT('https://picsum.photos/seed/all-my-trips-place-',i,'-2/1200/800'),CONCAT(region_name,' 추가 이미지'),2,FALSE);
  SET s1=CASE cat WHEN 'RESTAURANT' THEN 2 WHEN 'CAFE' THEN 5 WHEN 'ACCOMMODATION' THEN 1 WHEN 'ACTIVITY' THEN 4 ELSE 1 END; SET s2=CASE cat WHEN 'RESTAURANT' THEN 5 WHEN 'CAFE' THEN 3 WHEN 'FESTIVAL' THEN 4 ELSE 3 END;
  INSERT INTO place_travel_styles(place_id,travel_style_id,relevance_score,source) VALUES(i,s1,88-MOD(i,7),'MANUAL'),(i,s2,70-MOD(i,7),'MANUAL');
  SET i=i+1;
 END WHILE;
 SET i=1; WHILE i<=40 DO INSERT INTO favorites(user_id,place_id,memo) VALUES(MOD(i-1,8)+1,MOD(i*7-1,100)+1,IF(MOD(i,3)=0,CONCAT('후보 메모 ',i),NULL)); SET i=i+1; END WHILE;
END$$
DELIMITER ;
CALL seed_local_places(); DROP PROCEDURE seed_local_places; COMMIT;
