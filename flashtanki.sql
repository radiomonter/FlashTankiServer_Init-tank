-- phpMyAdmin SQL Dump
-- version 3.5.1
-- http://www.phpmyadmin.net
--
-- Хост: 127.0.0.1
-- Время создания: Май 29 2024 г., 00:03
-- Версия сервера: 5.5.25
-- Версия PHP: 5.3.13

SET SQL_MODE="NO_AUTO_VALUE_ON_ZERO";
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8 */;

--
-- База данных: `flashtanki`
--

-- --------------------------------------------------------

--
-- Структура таблицы `daily_quests`
--

CREATE TABLE IF NOT EXISTS `daily_quests` (
  `DTYPE` varchar(31) NOT NULL,
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `completed` bit(1) NOT NULL,
  `current` int(11) NOT NULL,
  `questIndex` int(11) NOT NULL,
  `new` bit(1) NOT NULL,
  `preview` int(11) NOT NULL,
  `required` int(11) NOT NULL,
  `bonus` int(11) DEFAULT NULL,
  `map` varchar(255) DEFAULT NULL,
  `mode` int(11) DEFAULT NULL,
  `user_id` int(11) DEFAULT NULL,
  PRIMARY KEY (`id`),
  KEY `idx_daily_quests_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Структура таблицы `daily_quest_rewards`
--

CREATE TABLE IF NOT EXISTS `daily_quest_rewards` (
  `rewardIndex` int(11) NOT NULL,
  `count` int(11) NOT NULL,
  `type` int(11) DEFAULT NULL,
  `quest_id` int(11) NOT NULL,
  PRIMARY KEY (`rewardIndex`,`quest_id`),
  KEY `FK1d0in0bdjfj5ihxdvv9r8f6hd` (`quest_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Структура таблицы `garage_items`
--

CREATE TABLE IF NOT EXISTS `garage_items` (
  `DTYPE` varchar(31) NOT NULL,
  `itemName` varchar(255) NOT NULL,
  `count` int(11) DEFAULT NULL,
  `modificationIndex` int(11) DEFAULT NULL,
  `endTime` bigint(20) DEFAULT NULL,
  `user_id` int(11) NOT NULL,
  PRIMARY KEY (`itemName`,`user_id`),
  KEY `FKg7k5oo20b4j5lrhwrskxel5i3` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8;

-- --------------------------------------------------------

--
-- Структура таблицы `invites`
--

CREATE TABLE IF NOT EXISTS `invites` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `code` varchar(64) NOT NULL,
  `username` varchar(64) DEFAULT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_svwtfl4u6ks2pppwum9bn4p1u` (`code`),
  KEY `idx_invites_code` (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;

-- --------------------------------------------------------

--
-- Структура таблицы `users`
--

CREATE TABLE IF NOT EXISTS `users` (
  `id` int(11) NOT NULL AUTO_INCREMENT,
  `bannedUntilMilliseconds` bigint(20) DEFAULT NULL,
  `canSkipQuestForFree` bit(1) NOT NULL,
  `chatModeratorLevel` int(11) NOT NULL,
  `crystals` int(11) NOT NULL,
  `currentQuestLevel` int(11) NOT NULL,
  `currentQuestStreak` int(11) NOT NULL,
  `equipment_hull_id` varchar(255) DEFAULT NULL,
  `equipment_paint_id` varchar(255) DEFAULT NULL,
  `equipment_resistance_id` varchar(255) DEFAULT NULL,
  `equipment_weapon_id` varchar(255) DEFAULT NULL,
  `hash` varchar(64) NOT NULL,
  `mutedUntilMilliseconds` bigint(20) DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `permissions` bigint(20) NOT NULL,
  `premium` int(11) NOT NULL,
  `score` int(11) NOT NULL,
  `username` varchar(64) NOT NULL,
  PRIMARY KEY (`id`),
  UNIQUE KEY `UK_gg6s1ono9io4qxwg2qy1r23r5` (`hash`),
  UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`),
  KEY `idx_users_username` (`username`),
  KEY `idx_users_hash` (`hash`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8 AUTO_INCREMENT=1 ;

--
-- Ограничения внешнего ключа сохраненных таблиц
--

--
-- Ограничения внешнего ключа таблицы `daily_quests`
--
ALTER TABLE `daily_quests`
  ADD CONSTRAINT `FK4lv7vasypc63q16mm9e3y5b9r` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

--
-- Ограничения внешнего ключа таблицы `daily_quest_rewards`
--
ALTER TABLE `daily_quest_rewards`
  ADD CONSTRAINT `FK1d0in0bdjfj5ihxdvv9r8f6hd` FOREIGN KEY (`quest_id`) REFERENCES `daily_quests` (`id`);

--
-- Ограничения внешнего ключа таблицы `garage_items`
--
ALTER TABLE `garage_items`
  ADD CONSTRAINT `FKg7k5oo20b4j5lrhwrskxel5i3` FOREIGN KEY (`user_id`) REFERENCES `users` (`id`);

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
