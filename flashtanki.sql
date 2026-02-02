-- phpMyAdmin SQL Dump
-- version 5.2.3
-- https://www.phpmyadmin.net/
--
-- Хост: localhost
-- Время создания: Янв 31 2026 г., 20:06
-- Версия сервера: 9.5.0
-- Версия PHP: 8.1.33

SET SQL_MODE = "NO_AUTO_VALUE_ON_ZERO";
START TRANSACTION;
SET time_zone = "+00:00";


/*!40101 SET @OLD_CHARACTER_SET_CLIENT=@@CHARACTER_SET_CLIENT */;
/*!40101 SET @OLD_CHARACTER_SET_RESULTS=@@CHARACTER_SET_RESULTS */;
/*!40101 SET @OLD_COLLATION_CONNECTION=@@COLLATION_CONNECTION */;
/*!40101 SET NAMES utf8mb4 */;

--
-- База данных: `flashtanki`
--

-- --------------------------------------------------------

--
-- Структура таблицы `clans`
--

CREATE TABLE `clans` (
  `id` int NOT NULL,
  `creatorId` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `description` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `name` varchar(64) COLLATE utf8mb4_general_ci NOT NULL,
  `tag` varchar(5) COLLATE utf8mb4_general_ci NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- --------------------------------------------------------

--
-- Структура таблицы `daily_quests`
--

CREATE TABLE `daily_quests` (
  `DTYPE` varchar(31) NOT NULL,
  `id` int NOT NULL,
  `completed` bit(1) NOT NULL,
  `current` int NOT NULL,
  `questIndex` int NOT NULL,
  `new` bit(1) NOT NULL,
  `preview` int NOT NULL,
  `required` int NOT NULL,
  `bonus` int DEFAULT NULL,
  `map` varchar(255) DEFAULT NULL,
  `mode` int DEFAULT NULL,
  `user_id` int DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `daily_quests`
--

INSERT INTO `daily_quests` (`DTYPE`, `id`, `completed`, `current`, `questIndex`, `new`, `preview`, `required`, `bonus`, `map`, `mode`, `user_id`) VALUES
('deliver_flag', 1, b'0', 0, 0, b'0', 123336, 2, NULL, NULL, NULL, 1),
('kill_enemy', 2, b'0', 0, 1, b'0', 123333, 2, NULL, NULL, 1, 1),
('take_bonus', 3, b'1', 5, 2, b'0', 123337, 2, 6, NULL, NULL, 1);

-- --------------------------------------------------------

--
-- Структура таблицы `daily_quest_rewards`
--

CREATE TABLE `daily_quest_rewards` (
  `rewardIndex` int NOT NULL,
  `count` int NOT NULL,
  `type` int DEFAULT NULL,
  `quest_id` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `daily_quest_rewards`
--

INSERT INTO `daily_quest_rewards` (`rewardIndex`, `count`, `type`, `quest_id`) VALUES
(0, 847, 1, 1),
(0, 1470, 1, 2),
(0, 857, 1, 3),
(1, 2, 2, 1),
(1, 2, 2, 2),
(1, 1, 2, 3);

-- --------------------------------------------------------

--
-- Структура таблицы `garage_items`
--

CREATE TABLE `garage_items` (
  `DTYPE` varchar(31) NOT NULL,
  `itemName` varchar(255) NOT NULL,
  `count` int DEFAULT NULL,
  `modificationIndex` int DEFAULT NULL,
  `endTime` bigint DEFAULT NULL,
  `user_id` int NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `garage_items`
--

INSERT INTO `garage_items` (`DTYPE`, `itemName`, `count`, `modificationIndex`, `endTime`, `user_id`) VALUES
('supply', 'armor', 445, NULL, NULL, 1),
('resistance', 'demon', NULL, NULL, NULL, 1),
('supply', 'double_damage', 570, NULL, NULL, 1),
('resistance', 'floralgc01', NULL, NULL, NULL, 1),
('supply', 'gold', 154, NULL, NULL, 1),
('paint', 'green', NULL, NULL, NULL, 1),
('supply', 'health', 571, NULL, NULL, 1),
('paint', 'holiday', NULL, NULL, NULL, 1),
('hull', 'hunter', NULL, 0, NULL, 1),
('hull', 'juggernaut', NULL, 0, NULL, 1),
('lootbox', 'lootbox', 200, NULL, NULL, 1),
('supply', 'mine', 570, NULL, NULL, 1),
('supply', 'n2o', 441, NULL, NULL, 1),
('paint', 'orange', NULL, NULL, NULL, 1),
('paint', 'picasso', NULL, NULL, NULL, 1),
('weapon', 'railgun_terminator', NULL, 0, NULL, 1),
('weapon', 'ricochet', NULL, 3, NULL, 1),
('weapon', 'smoky', NULL, 0, NULL, 1),
('hull', 'viking', NULL, 3, NULL, 1),
('resistance', 'zero', NULL, NULL, NULL, 1);

-- --------------------------------------------------------

--
-- Структура таблицы `invites`
--

CREATE TABLE `invites` (
  `id` int NOT NULL,
  `code` varchar(64) NOT NULL,
  `username` varchar(64) DEFAULT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

-- --------------------------------------------------------

--
-- Структура таблицы `news`
--

CREATE TABLE `news` (
  `id` varchar(64) NOT NULL,
  `image` varchar(255) NOT NULL,
  `date` varchar(32) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `news`
--

INSERT INTO `news` (`id`, `image`, `date`) VALUES
('1', 'http://127.0.0.1/assets/img/news.png', '17.01.2026');

-- --------------------------------------------------------

--
-- Структура таблицы `news_locale`
--

CREATE TABLE `news_locale` (
  `id` int NOT NULL,
  `news_id` varchar(64) NOT NULL,
  `locale` varchar(8) NOT NULL,
  `header` varchar(255) NOT NULL,
  `text` text NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `news_locale`
--

INSERT INTO `news_locale` (`id`, `news_id`, `locale`, `header`, `text`) VALUES
(4, '1', 'ru', '%USERNAME%', 'Уважаемые игроки FlashTanki!\r\n\r\nМы хотели бы выразить вам огромную благодарность за вашу преданность и активное участие в нашем проекте. Ваша игра в FlashTanki не только приносит вам удовольствие, но и является ценным вкладом в развитие игры. Благодаря вашему участию мы не только видим, какими талантливыми и страстными игроками вы являетесь, но также получаем ценные отзывы и обнаруживаем баги, которые помогают нам сделать проект еще лучше.\r\n\r\nВаша активность и внимательность к деталям помогают нам создавать более захватывающие и увлекательные игровые впечатления для всех. Спасибо, что вы с нами, спасибо за ваше терпение и отзывчивость. Мы ценим каждого из вас и обязуемся делать все возможное, чтобы FlashTanki продолжала приносить вам радость и удовлетворение.\r\n\r\nС наилучшими пожеланиями,\r\nКоманда разработчиков FlashTanki'),
(5, '1', 'en', '%USERNAME%', 'Dear FlashTanki players!\r\n\r\nWe would like to express our immense gratitude to you for your dedication and active participation in our project. Your gameplay in FlashTanki not only brings you enjoyment but also constitutes a valuable contribution to the development of the game. Thanks to your involvement, we not only see how talented and passionate players you are, but also receive valuable feedback and discover bugs that help us make the project even better. Your activity and attention to detail help us create more exciting and engaging gaming experiences for everyone. Thank you for being with us, thank you for your patience and responsiveness. We appreciate each and every one of you and pledge to do everything possible to ensure that FlashTanki continues to bring you joy and satisfaction.\r\n\r\nBest regards,\r\nThe FlashTanki Development Team');

-- --------------------------------------------------------

--
-- Структура таблицы `users`
--

CREATE TABLE `users` (
  `id` int NOT NULL,
  `bannedUntilMilliseconds` bigint DEFAULT NULL,
  `canSkipQuestForFree` bit(1) NOT NULL,
  `chatModeratorLevel` int NOT NULL,
  `crystals` int NOT NULL,
  `currentQuestLevel` int NOT NULL,
  `currentQuestStreak` int NOT NULL,
  `equipment_hull_id` varchar(255) DEFAULT NULL,
  `equipment_paint_id` varchar(255) DEFAULT NULL,
  `equipment_resistance_id` varchar(255) DEFAULT NULL,
  `equipment_weapon_id` varchar(255) DEFAULT NULL,
  `hash` varchar(64) NOT NULL,
  `mutedUntilMilliseconds` bigint DEFAULT NULL,
  `password` varchar(255) NOT NULL,
  `permissions` bigint NOT NULL,
  `premium` int NOT NULL,
  `score` int NOT NULL,
  `username` varchar(64) NOT NULL,
  `snId` varchar(64) NOT NULL
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb3;

--
-- Дамп данных таблицы `users`
--

INSERT INTO `users` (`id`, `bannedUntilMilliseconds`, `canSkipQuestForFree`, `chatModeratorLevel`, `crystals`, `currentQuestLevel`, `currentQuestStreak`, `equipment_hull_id`, `equipment_paint_id`, `equipment_resistance_id`, `equipment_weapon_id`, `hash`, `mutedUntilMilliseconds`, `password`, `permissions`, `premium`, `score`, `username`, `snId`) VALUES
(1, NULL, b'0', 0, 1445222, 0, 1, 'juggernaut', 'picasso', 'demon', 'railgun_terminator', '72b48c708801e08a9cd306bcea866e77', NULL, 'Prorab1979', 2, 17696760, 11039000, 'Maksim_Terehov', '1871c91020c77c47755a50a714e45bbc');

--
-- Индексы сохранённых таблиц
--

--
-- Индексы таблицы `clans`
--
ALTER TABLE `clans`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK_7opndv7tcrbqgbppex8d4icuu` (`creatorId`),
  ADD UNIQUE KEY `UK_s7yqhe0jkmwym07e416sv5m7x` (`description`),
  ADD UNIQUE KEY `UK_t41q1x8w4x2swr0xu8xhrluj0` (`name`),
  ADD UNIQUE KEY `UK_t3emntflrsesa1128643ibmkj` (`tag`),
  ADD KEY `idx_clans_tag` (`tag`),
  ADD KEY `idx_clans_name` (`name`);

--
-- Индексы таблицы `daily_quests`
--
ALTER TABLE `daily_quests`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_daily_quests_user` (`user_id`);

--
-- Индексы таблицы `daily_quest_rewards`
--
ALTER TABLE `daily_quest_rewards`
  ADD PRIMARY KEY (`rewardIndex`,`quest_id`),
  ADD KEY `FK1d0in0bdjfj5ihxdvv9r8f6hd` (`quest_id`);

--
-- Индексы таблицы `garage_items`
--
ALTER TABLE `garage_items`
  ADD PRIMARY KEY (`itemName`,`user_id`),
  ADD KEY `FKg7k5oo20b4j5lrhwrskxel5i3` (`user_id`);

--
-- Индексы таблицы `invites`
--
ALTER TABLE `invites`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK_svwtfl4u6ks2pppwum9bn4p1u` (`code`),
  ADD KEY `idx_invites_code` (`code`);

--
-- Индексы таблицы `news`
--
ALTER TABLE `news`
  ADD PRIMARY KEY (`id`);

--
-- Индексы таблицы `news_locale`
--
ALTER TABLE `news_locale`
  ADD PRIMARY KEY (`id`),
  ADD KEY `idx_news_locale_news_id` (`news_id`);

--
-- Индексы таблицы `users`
--
ALTER TABLE `users`
  ADD PRIMARY KEY (`id`),
  ADD UNIQUE KEY `UK_gg6s1ono9io4qxwg2qy1r23r5` (`hash`),
  ADD UNIQUE KEY `UK_r43af9ap4edm43mmtq01oddj6` (`username`),
  ADD UNIQUE KEY `UK_p6f1xp7mhwubvs1tl5y4e2x1c` (`snId`),
  ADD KEY `idx_users_username` (`username`),
  ADD KEY `idx_users_hash` (`hash`),
  ADD KEY `idx_users_snId` (`snId`);

--
-- AUTO_INCREMENT для сохранённых таблиц
--

--
-- AUTO_INCREMENT для таблицы `clans`
--
ALTER TABLE `clans`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT для таблицы `daily_quests`
--
ALTER TABLE `daily_quests`
  MODIFY `id` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=4;

--
-- AUTO_INCREMENT для таблицы `invites`
--
ALTER TABLE `invites`
  MODIFY `id` int NOT NULL AUTO_INCREMENT;

--
-- AUTO_INCREMENT для таблицы `news_locale`
--
ALTER TABLE `news_locale`
  MODIFY `id` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=6;

--
-- AUTO_INCREMENT для таблицы `users`
--
ALTER TABLE `users`
  MODIFY `id` int NOT NULL AUTO_INCREMENT, AUTO_INCREMENT=2;

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

--
-- Ограничения внешнего ключа таблицы `news_locale`
--
ALTER TABLE `news_locale`
  ADD CONSTRAINT `fk_news_locale_news` FOREIGN KEY (`news_id`) REFERENCES `news` (`id`) ON DELETE CASCADE;
COMMIT;

/*!40101 SET CHARACTER_SET_CLIENT=@OLD_CHARACTER_SET_CLIENT */;
/*!40101 SET CHARACTER_SET_RESULTS=@OLD_CHARACTER_SET_RESULTS */;
/*!40101 SET COLLATION_CONNECTION=@OLD_COLLATION_CONNECTION */;
