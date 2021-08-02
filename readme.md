# [Pixiv Helper](https://github.com/cssxsh/pixiv-helper)

> 基于 [Mirai Console](https://github.com/mamoe/mirai-console) 的 [Pixiv](https://www.pixiv.net/) 插件

基于 Kotlin Pixiv库 [PixivClient](https://github.com/cssxsh/pixiv-client) ，通过清除ServerHostName 绕过SNI审查，免代理

目前没有自动缓存清理，请使用 [#删除指令] 手动清理  
R18图会按照Pixiv所给信息过滤  
群聊模式使用默认账号，私聊模式Pixiv账号和QQ号关联，初次使用请先 `/login` 指令登陆账号  
然后使用 `/cache recommended` 缓存系统推荐作品 然后再使用色图相关指令  
群聊默认输出最少作品信息，需要增加请使用 `/setting` 指令修改  
百度云盘的相关配置是为了能够将数据或者图片备份至百度云盘，若无需要可以不配置

[![Release](https://img.shields.io/github/v/release/cssxsh/pixiv-helper)](https://github.com/cssxsh/pixiv-helper/releases)
[![Downloads](https://img.shields.io/github/downloads/cssxsh/pixiv-helper/total)](https://shields.io/category/downloads)
[![MiraiForum](https://img.shields.io/badge/post-on%20MiraiForum-yellow)](https://mirai.mamoe.net/topic/289)

## 指令

注意: 使用前请确保可以 [在聊天环境执行指令](https://github.com/project-mirai/chat-command)  
带括号的`/`前缀是可选的  
`<...>`中的是指令名，由空格隔开表示或，选择其中任一名称都可执行例如`/色图`  
`[...]`表示参数，当`[...]`后面带`?`时表示参数可选  
`{...}`表示连续的多个参数

`bookmark` 参数指收藏数过滤
`fuzzy` 参数指模糊搜索

### Pixiv相关操作指令

| 指令                                     | 描述                                       |
|:-----------------------------------------|:-------------------------------------------|
| `/<pixiv> <login> [username] [password]` | APP 不再支持 账号 密码 直接登录，指令做废  |
| `/<pixiv> <sina>`                        | 扫码登录关联了PIXIV的微博账号，以登录PIXIV |
| `/<pixiv> <cookie>`                      | 从文件 读取 Web Cookie，登录PIXIV          |
| `/<pixiv> <refresh< [token]`             | 登录 通过 refresh token                    |
| `/<follow> <user> {uid}`                 | 为当前助手关注指定用户                     |
| `/<follow> <good>`                       | 关注色图缓存中的较好画师                   |
| `/<follow> <copy> [uid]`                 | 关注指定用户的关注                         |
| `/<mark bookmark> <add> [uid] {words}?`  | 添加指定作品收藏                           |
| `/<mark bookmark> <delete> [pid]`        | 删除指定作品收藏                           |
| `/<mark bookmark> <random> [tag]?`       | 随机发送一个收藏的作品                     |
| `/<mark bookmark> <list>`                | 显示收藏列表                               |

cookie 文件为工作目录下的 `cookie.json`  
内容 为 浏览器插件 [EditThisCookie](http://www.editthiscookie.com/) 导出的Json  
EditThisCookie 安装地址
[Chrome](https://chrome.google.com/webstore/detail/editthiscookie/fngmhnnpilhplaeedifhccceomclgfbg)
[Firefox](https://addons.mozilla.org/firefox/downloads/file/3449327/editthiscookie2-1.5.0-fx.xpi)
[Edge](https://microsoftedge.microsoft.com/addons/getproductdetailsbycrxid/ajfboaconbpkglpfanbmlfgojgndmhmc?hl=zh-CN&gl=CN)

### 色图相关指令

| 指令                                                  | 描述                                  |
|:------------------------------------------------------|:--------------------------------------|
| `(/)<ero 色图 涩图>`                                  | 缓存中随机一张色图                    |
| `(/)<get 搞快点 gkd> [pid] [flush]?`                  | 获取指定ID图片                        |
| `(/)<tag 标签> [word] [bookmark]? [fuzzy]?`           | 随机指定TAG图片                       |
| `(/)<illustrator 画师> <uid id user 用户> [uid]`      | 根据画师UID随机发送画师作品           |
| `(/)<illustrator 画师> <name 名称 名字> [name]`       | 根据画师name或者alias随机发送画师作品 |
| `(/)<illustrator 画师> <alias 别名> [name] [uid]`     | 设置画师alias                         |
| `(/)<illustrator 画师> <list 列表>`                   | 显示别名列表                          |
| `(/)<illustrator 画师> <info 信息> [uid]`             | 获取画师信息                          |
| `(/)<illustrator 画师> <search 搜索> [name] [limit]?` | 搜索画师                              |
| `(/)<search 搜索 搜图> [image]`                       | 搜索图片                              |

色图指令基于缓存信息，使用前请先缓存一定量的作品，推荐使用 `/cache recommended` 指令  
使用色图指令时 指令后附带 `更好`, 可以使收藏数比前一张更高, 如果两次色图指令间隔小于触发时间(默认时间10s)也会触发这个效果  
tag指令检索结果过少时，会自动触发自动缓存  
回复带有图片的消息，也可以搜图，但是图片的消息必须已经被机器人记录  
搜图使用 <https://saucenao.com> 的 api 无KEY时，每天限额 100次， KEY参数在设置中添加

### 缓存指令

| 指令                             | 描述                                    |
|:---------------------------------|:----------------------------------------|
| `/<cache> <follow>`              | 缓存关注推送                            |
| `/<cache> <rank> [mode] [date]?` | 缓存指定排行榜信息                      |
| `/<cache> <recommended>`         | 从推荐画师的预览中缓存色图作品，ERO过滤 |
| `/<cache> <bookmarks> [uid]?`    | 从用户的收藏中缓存色图作品              |
| `/<cache> <following> [fluhsh]?` | 将关注画师列表检查，缓存所有作品        |
| `/<cache> <user> [uid]`          | 缓存指定画师作品                        |
| `/<cache> <tag> [tag]`           | 缓存搜索得到的tag，ERO过滤              |
| `/<cache> <search>`              | 缓存搜索记录                            |
| `/<cache> <stop>`                | 停止当前助手缓存任务                    |
| `/<cache> <reply> [open]`        | 为是否回复缓存细节，默认为否            |

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

| 指令                                         | 描述                |
|:---------------------------------------------|:--------------------|
| `/<task> <user> [uid] [duration]?`           | 推送用户新作品      |
| `/<task> <rank> [mode]`                      | 推送排行榜新作品    |
| `/<task> <follow> [duration]?`               | 推送关注用户作品    |
| `/<task> <recommended> [duration]?`          | 推送推荐作品        |
| `/<task> <backup> [duration]?`               | 数据自动备份        |
| `/<task> <web> [pattern] [link] [duration]?` | 推送，从url链接获取 |
| `/<task> <detail>`                           | 查看任务详情        |
| `/<task> <delete> [name]`                    | 删除任务            |

备份文件优先推送到群文件，其次百度云

duration 单位分钟，默认3小时

### 设置指令

| 指令                          | 描述                           |
|:------------------------------|:-------------------------------|
| `/<setting> <interval> [sec]` | 设置连续发送间隔时间, 单位秒   |
| `/<setting> <link> [open]`    | 设置是否显示Pixiv Cat 原图链接 |
| `/<setting> <tag> [open]`     | 设置是否显示TAG INFO           |
| `/<setting> <attr> [open]`    | 设置是否显示作品属性           |
| `/<setting> <max> [num]`      | 设置是否显示最大图片数         |

### 备份指令

| 指令                                         | 描述                       |
|:---------------------------------------------|:---------------------------|
| `/<backup> <user> [uid]`                     | 备份指定用户的作品         |
| `/<backup> <alias> [mode] [date]?`           | 备份已设定别名用户的作品   |
| `/<backup> <tag> [tag] [bookmark]? [fuzzy]?` | 备份指定标签的作品         |
| `/<backup> <data>`                           | 备份插件数据               |
| `/<backup> <list>`                           | 列出备份目录               |
| `/<backup> <get> [filename]`                 | 获取备份文件，发送文件消息 |
| `/<backup> <upload> [filename]`              | 上传插件数据到百度云       |
| `/<backup> <auth>`                           | 百度云用户认证             |

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

| 指令                                    | 描述                   |
|:----------------------------------------|:-----------------------|
| `/<delete> <artwork> [pid] [record]?`   | 删除指定作品           |
| `/<delete> <user> [uid] [record]?`      | 删除指定用户作品       |
| `/<delete> <bookmarks> [max] [record]?` | 删除小于指定收藏数作品 |
| `/<delete> <page> [min] [record]?`      | 删除大于指定页数作品   |

第二参数 record 表明是否写入数据库，默认为否，只删除图片文件

## 设置

### PixivHelperSettings.yml

缓存目录、涩图标准等  
proxy 代理，针对API不针对下载, `http://127.0.0.1:8080` or `socks://127.0.0.1:1080`  
pximg i.pximg.net反向代理域名，可以填入i.pixiv.cat，某些情况下可以解决下载缓慢的问题

### NetdiskOauthConfig.yml

插件备份文件功能需要百度网盘API支持，请到 <https://pan.baidu.com/union/main/application/personal> 申请应用，并将获得的APP信息填入  
信息只在启动时读取，修改后需重启，并使用 /backup auth 认证百度账号

### ImageSearchConfig.yml

KEY 不是必须的，无KEY状态下，根据IP每天可以搜索 100 次，有KEY状态下搜索次数依据于账户  
KEY 参数请到 <https://saucenao.com/> 注册账号， 在用户页面 <https://saucenao.com/user.php?page=search-api> 获得的KEY填入  
信息只在启动时读取，修改后需重启

### PixivSqlConfig.yml

1. url JDBC url 默认 为 `jdbc:sqlite:pixiv.sqlite`
1. driver 驱动类 只有 非SQLite 时生效
1. properties 驱动配置

MCL 配置 mysql 举例，其他数据库类推  
安装 驱动，`mysql:mysql-connector-java` 是驱动包名
`.\mcl --update-package mysql:mysql-connector-java --type libs --channel stable`
配置 链接

```
url: 'jdbc:mysql://localhost:3306/pixiv'
driver: 'com.mysql.cj.jdbc.Driver'
properties: 
  'user': 'root'
  'password': 'root'
```
