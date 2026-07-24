SET NAMES utf8mb4;

-- Drop video_job sharding tables (video_job_00 to video_job_15)
DROP TABLE IF EXISTS video_job_00;
DROP TABLE IF EXISTS video_job_01;
DROP TABLE IF EXISTS video_job_02;
DROP TABLE IF EXISTS video_job_03;
DROP TABLE IF EXISTS video_job_04;
DROP TABLE IF EXISTS video_job_05;
DROP TABLE IF EXISTS video_job_06;
DROP TABLE IF EXISTS video_job_07;
DROP TABLE IF EXISTS video_job_08;
DROP TABLE IF EXISTS video_job_09;
DROP TABLE IF EXISTS video_job_10;
DROP TABLE IF EXISTS video_job_11;
DROP TABLE IF EXISTS video_job_12;
DROP TABLE IF EXISTS video_job_13;
DROP TABLE IF EXISTS video_job_14;
DROP TABLE IF EXISTS video_job_15;

-- Drop the main video_job table
DROP TABLE IF EXISTS video_job;
