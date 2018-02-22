alter table print_order add column transfered boolean;
update print_order set transfered=true;
alter table print_order alter column transfered set not null;

alter table print_station add column transfer_proportion integer;
update print_station set transfer_proportion=1000;
alter table print_station alter column transfer_proportion set not null;

alter table print_order add column transfer_proportion integer;
update print_order set transfer_proportion=1000;
alter table print_order alter column transfer_proportion set not null;

update ad_set set company_id=0 where company_id=1;

alter table print_station alter column id drop default;
drop sequence print_station_id_seq;

alter table position drop column city_id;
drop table city cascade;

alter table position add column address_nation varchar(100);
alter table position add column address_province varchar(100);
alter table position add column address_city varchar(100);
alter table position add column address_district varchar(100);
alter table position add column address_street varchar(100);
update position set address_nation='中国',address_province='北京',address_city='北京',address_district='海淀区',address_street='';

alter table print_station drop column city_id;
alter table print_station add column address_nation varchar(100);
alter table print_station add column address_province varchar(100);
alter table print_station add column address_city varchar(100);
alter table print_station add column address_district varchar(100);
alter table print_station add column address_street varchar(100);
update print_station set address_nation='中国',address_province='北京',address_city='北京',address_district='海淀区',address_street='';

alter table print_station add column name varchar(50);
update print_station set name='';
alter table print_station alter column name set not null;

alter table company_wx_account add column sequence integer;
update company_wx_account set sequence=0;
alter table company_wx_account alter column sequence set not null;

alter table ad_set add column public_resource boolean;
update ad_set set public_resource=true, company_id=1;
alter table ad_set alter column public_resource set not null;

alter table wx_ent_transfer_record_item add column charge integer;
update wx_ent_transfer_record_item set charge=0;
alter table wx_ent_transfer_record_item alter column charge set not null;