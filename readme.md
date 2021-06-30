# PIXIV助手

> 基于 [Mirai Console](https://github.com/mamoe/mirai-console) 的 [Pixiv](https://www.pixiv.net/) 插件

基于 Kotlin Pixiv库 [PixivClient](https://github.com/cssxsh/pixiv-client) ，通过清除ServerHostName 绕过SNi审查，免代理

目前缺乏缓存清理，请手动清理 R18图会按照Pixiv所给信息过滤

群聊模式使用默认账号，私聊模式Pixiv账号和QQ号关联

[![Release](https://img.shields.io/github/v/release/cssxsh/pixiv-helper)](https://github.com/cssxsh/pixiv-helper/releases)
[![Downloads](https://img.shields.io/github/downloads/cssxsh/pixiv-helper/total)](https://shields.io/category/downloads)
[![MiraiForum](https://img.shields.io/badge/post-on%20MiraiForum-yellow)](https://mirai.mamoe.net/topic/289)

## 指令

注意: 使用前请确保可以 [在聊天环境执行指令](https://github.com/project-mirai/chat-command)  
带括号的`/`前缀是可选的  
`<...>`中的是指令名，由空格隔开表示或，选择其中任一名称都可执行例如`/色图`  
`[...]`表示参数，当`[...]`后面带`?`时表示参数可选  
`{...}`表示连续的多个参数

### Pixiv相关操作指令

| 指令                                     | 描述                     |
|:-----------------------------------------|:-------------------------|
| `/<pixiv> <login> [username] [password]` | 登录 通过 用户名，密码   |
| `/<pixiv> <refresh< [token]`             | 登录 通过 refresh token  |
| `/<follow> <user< {uid}`                 | 为当前助手关注指定用户   |
| `/<follow> <good>`                       | 关注色图缓存中的较好画师 |
| `/<follow> <copy> [uid]`                 | 关注指定用户的关注       |
| `/<mark bookmark> <add> [uid] {words}?`  | 添加指定作品收藏         |
| `/<mark bookmark> <delete> [pid]`        | 删除指定作品收藏         |
| `/<mark bookmark> <random> [tag]?`       | 随机发送一个收藏的作品   |
| `/<mark bookmark> <list>`                | 显示收藏列表             |

### 色图相关指令

| 指令                                                  | 描述                                  |
|:------------------------------------------------------|:--------------------------------------|
| `(/)<ero 色图 涩图>`                                  | 缓存中随机一张色图                    |
| `(/)<get 搞快点 gkd> [pid] [flush]?`                  | 获取指定ID图片                        |
| `(/)<tag 标签> [word] [bookmark]?`                    | 随机指定TAG图片                       |
| `(/)<illustrator 画师> <uid id user 用户> [uid]`      | 根据画师UID随机发送画师作品           |
| `(/)<illustrator 画师> <name 名称 名字> [name]`       | 根据画师name或者alias随机发送画师作品 |
| `(/)<illustrator 画师> <alias 别名> [name] [uid]`     | 设置画师alias                         |
| `(/)<illustrator 画师> <list 列表>`                   | 显示别名列表                          |
| `(/)<illustrator 画师> <info 信息> [uid]`             | 获取画师信息                          |
| `(/)<illustrator 画师> <search 搜索> [name] [limit]?` | 搜索画师                              |
| `(/)<search 搜索 搜图> [image]`                       | 搜索图片                              |

搜图使用 <https://saucenao.com> 的 api 每天限额 100次， 
回复带有图片的消息，也可以搜图，但是图片的消息必须已经被机器人记录

### 缓存指令

| 指令                             | 描述                                    |
|:---------------------------------|:----------------------------------------|
| `/<cache> <follow>`              | 缓存关注推送                            |
| `/<cache> <rank> [mode] [date]?` | 缓存指定排行榜信息                      |
| `/<cache> <recommended>`         | 从推荐画师的预览中缓存色图作品，ERO过滤 |
| `/<cache> <bookmarks> [uid]?`    | 从用户的收藏中缓存色图作品              |
| `/<cache> <following>`           | 将关注画师列表检查，缓存所有作品        |
| `/<cache> <user> [uid]`          | 缓存指定画师作品                        |
| `/<cache> <tag> [tag]`           | 缓存搜索得到的tag，ERO过滤              |
| `/<cache> <search>`              | 缓存搜索记录                            |
| `/<cache> <stop>`                | 停止当前助手缓存任务                    |

RankMode

```
MONTH

WEEK
WEEK_ORIGINAL
WEEK_ROOKIE

DAY
DAY_MALE
DAY_FEMALE
DAY_MANGA
```

### 任务指令

| 指令                                         | 描述                       |
|:---------------------------------------------|:---------------------------|
| `/<task> <user> [uid] [duration]?`           | 推送用户新作品             |
| `/<task> <rank> [mode]`                      | 推送排行榜新作品           |
| `/<task> <follow> [duration]?`               | 推送关注用户作品           |
| `/<task> <recommended> [duration]?`          | 推送推荐作品               |
| `/<task> <backup> [duration]?`               | 获取备份文件，发送文件消息 |
| `/<task> <web> [pattern] [link] [duration]?` | 推送，从url链接获取        |
| `/<task> <detail>`                           | 查看任务详情               |

duration 单位分钟，默认3小时

### 设置指令

| 指令                          | 描述                           |
|:------------------------------|:-------------------------------|
| `/<setting> <interval> [sec]` | 设置连续发送间隔时间, 单位秒   |
| `/<setting> <link> [link]`    | 设置是否显示Pixiv Cat 原图链接 |

### 备份指令

| 指令                               | 描述                                    |
|:------------------------------------|:---------------------------------------|
| `/<backup> <user> [uid]`            | 备份指定用户的作品                     |
| `/<backup> <alias> [mode] [date]?`  | 备份已设定别名用户的作品               |
| `/<backup> <tag> [tag] [bookmark]?` | 备份指定标签的作品，第二参数为收藏过滤 |
| `/<backup> <data>`                  | 备份插件数据                           |
| `/<backup> <list>`                  | 列出备份目录                           |
| `/<backup> <get> [filename]`        | 获取备份文件，发送文件消息             |
| `/<backup> <upload> [filename]`     | 上传插件数据到百度云                   |
| `/<backup> <auth>`                  | 百度云用户认证                         |

### 统计信息指令

| 指令                        | 描述                |
|:----------------------------|:--------------------|
| `/<info> <helper>`          | 获取助手信息        |
| `/<info> <user> [target]?`  | 获取用户信息        |
| `/<info> <group> [target]?` | 获取群组信息        |
| `/<info> <top> [limit]?`    | 获取TAG指令统计信息 |
| `/<info> <cache>`           | 获取缓存信息        |


使用百度云服务需要的准备详见配置

### 播放指令

| 指令                                             | 描述                    |
|:-------------------------------------------------|:------------------------|
| `(/)<play 播放> <interval 间隔>`                 | 设置间隔                |
| `(/)<play 播放> <ranking 排行榜> [mode] [date]?` | 设置间隔                |
| `(/)<play 播放> <rank 排行> {words}`             | 根据 words 播放NaviRank |
| `(/)<play 播放> <recommended 推荐>`              | 根据 系统推荐 播放图集  |
| `(/)<play 播放> <mark 收藏> [tag]?`              | 播放收藏                |
| `(/)<play 播放> <article 特辑> [aid]`            | 播放特辑                |
| `(/)<article 特辑>`                              | 随机播放特辑            |
| `(/)<rank 排行> <year 年 年榜> {words}`          | 随机播放NaviRank 年榜   |
| `(/)<rank 排行> <month 月 月榜> {words}`         | 随机播放NaviRank 月榜   |
| `(/)<rank 排行> <tag标签> {words}`               | 随机播放NaviRank 标签榜 |
| `(/)<play 播放> <stop 停止>`                     | 停止播放当前列表        |

使用百度云服务需要的准备详见配置

### 删除指令

| 指令                                | 描述                   |
|:------------------------------------|:-----------------------|
| `/<delete> <artwork> [pid]`         | 删除指定作品           |
| `/<delete> <user> [user]`           | 删除指定用户作品       |
| `/<delete> <bookmarks> [bookmarks]` | 删除小于指定收藏数作品 |

## 设置

### PixivHelperSettings.yml

缓存目录、涩图标准等

### NetdiskOauthConfig.yml

插件需要百度网盘API支持，请到 https://pan.baidu.com/union/main/application/personal 申请应用，并将获得的APP信息填入
并使用 /backup auth 认证百度账号
