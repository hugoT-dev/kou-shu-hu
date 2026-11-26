# 护理员端微信小程序 MVP Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** 做出护理员微信小程序和它依赖的登录、班次、原声、流式识别与交接接口，使 Android 与 iOS 微信都能走完「说一句 → 确认 → 交班」。

**Architecture:** 原生小程序只访问网关。护理服务保存会话、排班、原声对象键和交接清单。语音服务把 16k PCM 转给 DashScope。对象存储通过 `ObjectStore` 端口，测试用内存实现，部署用阿里云 OSS。

**Tech Stack:** 微信原生小程序、Spring Boot 3.2.12、Spring Cloud Gateway、JPA、H2（测试）/ MySQL（部署）、DashScope paraformer-realtime-v2。

## Global Constraints

- 小程序放在 `miniprogram/`，原生页面，不使用 uni-app、Taro 或 web-view。
- 不新增微服务。登录、班次、老人、记录、交接都在 `yl-nursing`。
- 班次边界使用 `Asia/Shanghai`。默认白班 08:00–20:00，夜班 20:00–次日 08:00。
- 原声对象键：`originals/{staffId}/{yyyyMMdd}/{uuid}.pcm`。影像对象键：`media/{recordOrItemId}/{uuid}.{ext}`。
- 会话是服务端保存的不透明令牌，有效期 7 天。小程序不持久保存 openId。
- 原声短于 1 秒拒绝写入。最长 60 秒。短视频不超过 15 秒。
- 确认后的原文、原声、影像不能删除或覆盖。
- 首页「确认」按钮固定不可用，文案为「接班确认下一期开放」。
- 没有 `DASHSCOPE_API_KEY` 时录音链路失败，错误文案包含「未配置 yl.asr.dashscope.api-key」，不得返回假想终稿。
- 签名 URL 有效期 10 分钟。
- 自动化测试不连接微信、DashScope 或 OSS。护理服务测试使用 H2，并设置 `spring.cloud.nacos.discovery.enabled=false`。
- 现有 `frontend/` 与 FunASR 路径不删除。
- 一个微信号只绑定一个人员。院长和护理主管登录小程序时消息为「院长端下一期开放」。

---

### Task 1: 微信登录与会话

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/WechatCodeClient.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/AuthService.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/AuthController.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/StaffSession.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/repo/StaffSessionRepo.java`
- Modify: `backend/yl-common/src/main/java/com/ylcare/common/ApiResult.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/Staff.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/AuthLoginTest.java`

**Interfaces:**
- Consumes: 现有 `Staff`、`StaffRepo`、`ApiResult`
- Produces: `WechatCodeClient.openIdFor(String code)`；`AuthService.login(String code)` 返回 `ApiResult`，成功时 `data.token`、`data.role`、`data.name`；失败时 `code` 为 `UNBOUND` 或 `DISABLED`。`StaffSession.token` 有效期为 `Instant.now().plus(Duration.ofDays(7))`。

- [ ] **Step 1: Write the failing test**

在 `AuthLoginTest` 中使用与 `RecordApiTest` 相同的 `@SpringBootTest` 和关闭 Nacos、H2 内存库的 `@TestPropertySource`。提供测试用 `WechatCodeClient`：`code` 原样当作 openId。

```java
@Test
void unboundCodeDoesNotIssueToken() {
    ResponseEntity<ApiResult> res = rest.postForEntity("/api/auth/wechat", Map.of("code", "openid-new"), ApiResult.class);
    assertFalse(res.getBody().isOk());
    assertEquals("UNBOUND", res.getBody().getCode());
    assertNull(res.getBody().getData());
}

@Test
void boundEnabledCaregiverReceivesSevenDayToken() {
    Staff staff = new Staff();
    staff.setName("李护理");
    staff.setRole("caregiver");
    staff.setWechatOpenId("openid-li");
    staff.setEnabled(true);
    staffRepo.save(staff);
    ApiResult<?> body = rest.postForObject("/api/auth/wechat", Map.of("code", "openid-li"), ApiResult.class);
    assertTrue(body.isOk());
    Map<?, ?> data = (Map<?, ?>) body.getData();
    assertEquals("caregiver", data.get("role"));
    assertNotNull(data.get("token"));
    StaffSession session = sessionRepo.findByToken((String) data.get("token")).orElseThrow();
    long days = Duration.between(Instant.now(), session.getExpiresAt()).toDays();
    assertEquals(7, days);
}
```

再写一个已停用人员返回 `DISABLED` 且 `data == null` 的测试。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=AuthLoginTest`
Expected: FAIL，编译错误指向 `getCode` 或 `/api/auth/wechat` 不存在。

- [ ] **Step 3: Write minimal implementation**

`ApiResult` 增加 `code` 字段和 `fail(String code, String message)`。`Staff` 增加可空唯一列 `wechatOpenId` 和 `enabled`，默认 `true`。

`WechatCodeClient` 只有 `String openIdFor(String code)`。生产实现读取 `WECHAT_APPID` 与 `WECHAT_SECRET`，请求 `https://api.weixin.qq.com/sns/jscode2session`。测试配置类用 `@Primary` 替换它。

`AuthService.login`：用 openId 找人员。找不到返回 `ApiResult.fail("UNBOUND", "联系院长开通")`。`enabled == false` 返回 `ApiResult.fail("DISABLED", "账号已停用")`。否则生成 32 字节随机令牌的十六进制字符串，保存 `StaffSession`，过期时间为现在加 7 天，返回 token、role、name。

`AuthController`：`POST /api/auth/wechat`，正文 `code`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=AuthLoginTest`
Expected: PASS，3 tests。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-common/src/main/java/com/ylcare/common/ApiResult.java backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/Staff.java backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/StaffSession.java backend/yl-nursing/src/main/java/com/ylcare/nursing/repo/StaffSessionRepo.java backend/yl-nursing/src/main/java/com/ylcare/nursing/auth backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/AuthLoginTest.java
git commit -m "feat: 微信 code 登录签发七天会话"
```

### Task 2: 护理接口要求会话

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/SessionAuthInterceptor.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/WebConfig.java`
- Modify: `backend/yl-nursing/src/test/java/com/ylcare/nursing/RecordApiTest.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/SessionRequiredTest.java`

**Interfaces:**
- Consumes: `StaffSessionRepo.findByToken(String token)`
- Produces: 请求属性 `staffId`（Long）。`Authorization: Bearer {token}` 无效或过期时 HTTP 401，正文 `code` 为 `SESSION_EXPIRED`。`/api/auth/wechat` 不拦截。

- [ ] **Step 1: Write the failing test**

```java
@Test
void nursingMetaWithoutTokenIsUnauthorized() {
    ResponseEntity<ApiResult> res = rest.getForEntity("/api/nursing/meta/staff", ApiResult.class);
    assertEquals(401, res.getStatusCode().value());
    assertEquals("SESSION_EXPIRED", res.getBody().getCode());
}
```

`RecordApiTest` 在每次请求前登录已绑定护理员，并把 `Authorization` 放进 `HttpHeaders`。`DataLoader` 创建的「李护理」要有 openId `openid-li`，测试用的 `WechatCodeClient` 与 Task 1 相同。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=SessionRequiredTest,RecordApiTest`
Expected: `SessionRequiredTest` FAIL，因为现在无令牌仍返回 200。

- [ ] **Step 3: Write minimal implementation**

拦截器跳过 `POST /api/auth/wechat`。其他 `/api/**` 读取 Bearer 令牌，找到未过期会话后把 `staffId` 放入 request attribute。否则写 401 JSON：`{"ok":false,"code":"SESSION_EXPIRED","message":"请重新登录"}`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=SessionRequiredTest,RecordApiTest,AuthLoginTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/auth backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/SessionRequiredTest.java backend/yl-nursing/src/test/java/com/ylcare/nursing/RecordApiTest.java backend/yl-nursing/src/main/java/com/ylcare/nursing/bootstrap/DataLoader.java
git commit -m "feat: 护理接口要求有效会话"
```

### Task 3: 人员绑定接口

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminStaffController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/BindStaffTest.java`

**Interfaces:**
- Consumes: 请求属性 `staffId`，`Staff.role`
- Produces: `POST /api/nursing/admin/staff/{id}/bind` 正文 `{"openId":"...","enabled":true}`。调用者角色不是 `admin` 或 `head_nurse` 时 HTTP 403，`code` 为 `FORBIDDEN`。同一 openId 已绑其他人时 HTTP 409，`code` 为 `OPENID_TAKEN`。

- [ ] **Step 1: Write the failing test**

护理员调用绑定返回 403。院长调用绑定后，该 openId 可以登录并得到被绑定人员的姓名。重复把同一 openId 绑到第二个人返回 409。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=BindStaffTest`
Expected: FAIL，404。

- [ ] **Step 3: Write minimal implementation**

控制器从 request attribute 取调用者，角色不是 `admin` 或 `head_nurse` 则 403。按 id 更新 `wechatOpenId` 与 `enabled`。保存前若另一个人员已持有该 openId，抛 409。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=BindStaffTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminStaffController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/auth/BindStaffTest.java
git commit -m "feat: 院长或主管绑定微信身份"
```

### Task 4: 班次、排班与首页

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/ShiftTemplate.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/ShiftAssignment.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/service/ShiftService.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/HomeController.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminShiftController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/ShiftHomeTest.java`

**Interfaces:**
- Consumes: `Staff`、在住老人数量、`NursingRecordRepo`
- Produces: `ShiftService.assignmentAt(Long staffId, ZonedDateTime shanghai)`。`GET /api/nursing/me/home` 对护理员返回 `shiftName`、`shiftStart`、`shiftEnd`、`elderlyCount`、`confirmedCount`、`recordEnabled`、`handoverEnabled`、`confirmEnabled=false`、`confirmReason=接班确认下一期开放`。未排班时三个按钮的 enabled 都是 false，`message` 为「联系管理员排班」。`admin` 与 `head_nurse` 的 `message` 为「院长端下一期开放」，且不返回人数。

- [ ] **Step 1: Write the failing test**

用 `Clock.fixed` 无法注入时，在测试里把「现在」通过 `ShiftService` 的包可见方法 `assignmentAt(staffId, ZonedDateTime.of(2026,11,26,8,30,0,0,ZoneId.of("Asia/Shanghai")))` 断言为白班。首页测试：已排班护理员 `recordEnabled=true`，`confirmEnabled=false`。未排班护理员 `recordEnabled=false` 且 message 为「联系管理员排班」。院长首页 message 为「院长端下一期开放」。

启动时若不存在班次模板，`ShiftService` 写入白班 08:00–20:00 与夜班 20:00–08:00。20:30 属于夜班，且夜班的排班日期是开始日。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ShiftHomeTest`
Expected: FAIL，首页 404。

- [ ] **Step 3: Write minimal implementation**

`ShiftTemplate` 保存 `name`、`startTime`、`endTime`（`LocalTime`）。`ShiftAssignment` 保存 `shiftDate`、`templateId`、`staffId`。跨日班次：若 `endTime` 不晚于 `startTime`，则从开始日的 `startTime` 到次日 `endTime`。

`GET /api/nursing/me/home` 使用 `ZonedDateTime.now(ZoneId.of("Asia/Shanghai"))`。已确认条数只数该人员、该排班日期和班次名称、状态为 `confirmed` 的记录。在住老人数为 `status=in` 的老人。本期排班不按区域过滤。

`POST /api/nursing/admin/shifts/assignments` 仅 `admin` 或 `head_nurse` 可调用，正文为日期、模板 id、人员 id。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ShiftHomeTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/ShiftTemplate.java backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/ShiftAssignment.java backend/yl-nursing/src/main/java/com/ylcare/nursing/service/ShiftService.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/HomeController.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminShiftController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/ShiftHomeTest.java
git commit -m "feat: 按北京时间返回护理员首页班次"
```

### Task 5: 在住老人与退住

**Files:**
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminElderlyController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/ElderlyAdminTest.java`

**Interfaces:**
- Consumes: `ElderlyRepo.findByStatus("in")`
- Produces: `GET /api/nursing/meta/elderly` 只含 `status=in`。`POST /api/nursing/admin/elderly` 与 `POST /api/nursing/admin/elderly/{id}/discharge` 仅院长或主管可调用。退住把 `status` 设为 `out`。

- [ ] **Step 1: Write the failing test**

名册同时有在住「王奶奶」和退住「赵奶奶」时，`/api/nursing/meta/elderly` 只有王奶奶。护理员调用新增老人返回 403。院长新增「3床 王奶奶」别名「王奶奶」后，meta 列表能看到她。出院后再查询 meta 不再出现。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ElderlyAdminTest`
Expected: FAIL。现有 meta 若尚未过滤，退住老人仍会出现；出院接口 404。

- [ ] **Step 3: Write minimal implementation**

`NursingController.elderly()` 改为 `elderlyRepo.findByStatus("in")`。管理接口校验角色后保存或把状态改为 `out`。床号在在住范围内唯一：若已有在住同床号，返回 409，`code` 为 `BED_TAKEN`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ElderlyAdminTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/AdminElderlyController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/ElderlyAdminTest.java
git commit -m "feat: 只把在住老人交给录音匹配"
```

### Task 6: 原声对象存储

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/storage/ObjectStore.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/storage/MemoryObjectStore.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/storage/OssObjectStore.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/service/OriginalAudioService.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/OriginalAudioController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/OriginalAudioTest.java`

**Interfaces:**
- Consumes: 会话中的 `staffId`
- Produces: `ObjectStore.put(String key, byte[] body)`、`ObjectStore.exists(String key)`、`ObjectStore.delete(String key)`、`ObjectStore.sign(String key, Duration ttl)`。`POST /api/nursing/original-audio` 的 multipart 字段 `durationMs` 与文件 `audio`。成功 `data.objectKey` 匹配 `originals/{staffId}/{yyyyMMdd}/{uuid}.pcm`，`data.durationMs` 为传入值。`durationMs < 1000` 时 HTTP 400，`code` 为 `TOO_SHORT`，且 `ObjectStore` 中没有新对象。

- [ ] **Step 1: Write the failing test**

测试配置 `@Bean @Primary MemoryObjectStore`。上传 `durationMs=2000` 和 32 字节 PCM，断言对象存在且键以前缀 `originals/` 加人员 id 开头。再上传 `durationMs=999`，断言 400 且对象数量不增加。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=OriginalAudioTest`
Expected: FAIL，404。

- [ ] **Step 3: Write minimal implementation**

`MemoryObjectStore` 用 `ConcurrentHashMap<String, byte[]>`。`sign` 在本任务就实现，返回 `memory://{key}?exp={nowPlusTenMinutesEpochSecond}`。`OssObjectStore` 在 `yl.storage.kind=oss` 时启用，使用阿里云 OSS SDK，密钥来自 `OSS_ENDPOINT`、`OSS_BUCKET`、`OSS_ACCESS_KEY_ID`、`OSS_ACCESS_KEY_SECRET`。默认测试和本地用 `yl.storage.kind=memory`。部署把 `SPRING_DATASOURCE_URL` 设为 MySQL；测试属性继续使用 H2 内存库，不在本任务改测试库。

`OriginalAudioService.store` 先检查时长，再生成键并 `put`。控制器不创建 `NursingRecord`。

在 `yl-nursing/pom.xml` 增加 `com.aliyun.oss:aliyun-sdk-oss:3.18.1`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=OriginalAudioTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/pom.xml backend/yl-nursing/src/main/java/com/ylcare/nursing/storage backend/yl-nursing/src/main/java/com/ylcare/nursing/service/OriginalAudioService.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/OriginalAudioController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/OriginalAudioTest.java
git commit -m "feat: 松开后立刻保存不少于一秒的原声"
```

### Task 7: 确认、班次归属与更正

**Files:**
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/NursingRecord.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/service/RecordService.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/ConfirmRecordTest.java`

**Interfaces:**
- Consumes: `OriginalAudioService` 已写入的 `objectKey`，`ShiftService.assignmentAt`
- Produces: `NursingRecord.recognitionStatus` 取值 `streaming`、`final`、`failed_audio_kept`。`shiftDate`、`shiftName`、`correctionOfId`。`RecordService.confirm(Long id)` 在 `elderlyId == null` 时抛 400，`code` 为 `ELDERLY_REQUIRED`。确认时刻按 `Asia/Shanghai` 落入班次。`POST /api/nursing/records` 增加字段 `objectKey`、`recognitionStatus`、`correctionOfId`。对已确认记录再次 `addMedia(original_audio)` 仍返回 409。

- [ ] **Step 1: Write the failing test**

1. 只上传症状文本、不传老人、也不含床号：`confirm` 返回 400 `ELDERLY_REQUIRED`。
2. 文本「5床李爷爷，下午开始咳嗽」且名册有在住李爷爷：确认后 `recordType=abnormal`，`shiftName=白班`（把确认时钟固定为 2026-11-26T08:30+08:00）。方法是给 `RecordService` 增加 `confirm(Long id, Instant at)`，控制器传入 `Instant.now()`。测试直接调用 service。
3. 对已确认记录以 `correctionOfId` 再创建一条：新记录 `correctionOfId` 等于原 id，原记录 `audioPath` 不变。
4. `recognitionStatus=failed_audio_kept` 且已有 objectKey 时，确认后 `audioPath` 仍是该键。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ConfirmRecordTest`
Expected: FAIL，无 `ELDERLY_REQUIRED`。

- [ ] **Step 3: Write minimal implementation**

`confirm` 在没有 `elderlyId` 时返回 400。有老人时写入 `confirmed`、`confirmedAt`，并用 `ShiftService.assignmentAt(staffId, at.atZone(ZoneId.of("Asia/Shanghai")))` 填班次。创建记录时若带 `objectKey`，把它存进 `audioPath` 和一条 `original_audio` 媒体，不再把文件写到本地磁盘。`correctionOfId` 只允许指向已确认记录。

短视频：`addMedia` 在 `mediaType=video` 且 `durationMs > 15000` 时返回 400，`code` 为 `VIDEO_TOO_LONG`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=ConfirmRecordTest,RecordApiTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/NursingRecord.java backend/yl-nursing/src/main/java/com/ylcare/nursing/service/RecordService.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/ConfirmRecordTest.java
git commit -m "feat: 确认记录归属班次并保留更正链接"
```

### Task 8: 四段交接清单

**Files:**
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/service/HandoverService.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/HandoverSectionsTest.java`

**Interfaces:**
- Consumes: 已确认记录的 `recordType`、`shiftDate`、`shiftName`、`staffId`
- Produces: `HandoverService.current(Long staffId, LocalDate shiftDate, String shiftName)`。摘要格式为「负责{n}人，平稳{n}人，需重点关注{n}人。」，且不得等于「一切正常」。异常记录生成 `itemType=focus`，遗留生成 `leftover`，物资生成 `supply`。没有物资记录时 `data.supplyNote` 为「本班未口述物资情况，请补充或确认无异常」。已提交清单再次 `current` 不改变条目。`POST /api/nursing/handovers/{id}/supply-ack` 把 `supplyAcknowledged=true`，之后没有物资记录也可以 `submit`。

- [ ] **Step 1: Write the failing test**

本班确认一条咳嗽异常和一条「家属交代明天上午送药」。`current` 的 focus 含咳嗽，leftover 含送药，摘要含「需重点关注1人」。只有常规记录时 focus 为空，仍可提交。没有任何物资记录时，未调用 supply-ack 就 submit 返回 400 `SUPPLY_UNACKED`；调用后 submit 成功。提交后再确认一条新记录并 `current`，条目数不变。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=HandoverSectionsTest`
Expected: FAIL。现有摘要是「本班已确认 N 条记录」。

- [ ] **Step 3: Write minimal implementation**

`rebuildItems` 只收集该人员、该班次日期和名称下的已确认记录。按 `recordType` 分流，不再把家属交代放进 focus。摘要用负责人数（在住老人总数）、重点关注条数、平稳人数 = 负责人数减去有异常记录的老人数，人数最小为 0。`HandoverSheet` 增加 `supplyAcknowledged`。`submit` 在没有 supply 条目且未确认时返回 400。状态已是 `submitted` 时 `rebuildItems` 直接返回。

照片缺失不改变 `submit` 的成功条件。响应里的 focus 条目增加 `photoHint=可补拍现场`，当该来源记录没有 `mediaType=photo` 时。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=HandoverSectionsTest,RecordApiTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/service/HandoverService.java backend/yl-nursing/src/main/java/com/ylcare/nursing/domain/HandoverSheet.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/HandoverSectionsTest.java
git commit -m "feat: 交接清单按四段生成并在提交后锁定"
```

### Task 9: 十分钟签名地址

**Files:**
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/SignedMediaUrlTest.java`

**Interfaces:**
- Consumes: `ObjectStore.sign(String key, Duration ttl)`
- Produces: `GET /api/nursing/media/{id}/url` 的 `data.url` 与 `data.expiresAt`。`expiresAt` 比现在晚 10 分钟，误差允许 5 秒。`MemoryObjectStore.sign` 返回 `memory://{key}?exp={epochSecond}`。

- [ ] **Step 1: Write the failing test**

先走原声上传和创建记录，取出 `original_audio` 的媒体 id，请求签名地址，断言 url 以 `memory://originals/` 开头，并且过期时间在 9 分 55 秒到 10 分 5 秒之间。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=SignedMediaUrlTest`
Expected: FAIL，404。

- [ ] **Step 3: Write minimal implementation**

控制器按媒体 id 取 `path` 作为对象键，调用 `objectStore.sign(path, Duration.ofMinutes(10))`。不要把永久 OSS 地址返回给客户端。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=SignedMediaUrlTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/web/NursingController.java backend/yl-nursing/src/main/java/com/ylcare/nursing/storage/MemoryObjectStore.java backend/yl-nursing/src/test/java/com/ylcare/nursing/SignedMediaUrlTest.java
git commit -m "feat: 媒体播放使用十分钟签名地址"
```

### Task 10: DashScope PCM 与重试识别

**Files:**
- Create: `backend/yl-asr/src/main/java/com/ylcare/asr/engine/DashScopePcmClient.java`
- Modify: `backend/yl-asr/src/main/java/com/ylcare/asr/engine/DashScopeStreamingAsrEngine.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/service/RetryAsrClient.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/web/OriginalAudioController.java`
- Test: `backend/yl-asr/src/test/java/com/ylcare/asr/engine/DashScopePcmTest.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/RetryAsrTest.java`

**Interfaces:**
- Consumes: `StreamingAsrEngine.open(Consumer<AsrEvent>)`
- Produces: `DashScopePcmClient.open(Consumer<AsrEvent> sink)`。密钥为空时 `DashScopeStreamingAsrEngine.open` 立刻发送 `AsrEvent.error("未配置 yl.asr.dashscope.api-key")`，并且不发送 `event=final`。有密钥时 `sendPcm` 把字节交给 client，`finish` 后 client 可以发出 partial 和 final。`POST /api/nursing/original-audio/{objectKey}/retry-asr` 失败时响应 `recognitionStatus=failed_audio_kept`，`ObjectStore.exists(objectKey)` 仍为 true，键字符串不变。

- [ ] **Step 1: Write the failing test**

`DashScopePcmTest` 不设 api key，调用 `open`，收集事件，断言唯一事件是 error 且文本包含 `未配置 yl.asr.dashscope.api-key`，没有 final。

第二个测试注入假的 `DashScopePcmClient`：`sendPcm` 保存字节，`finish` 先 partial「咳嗽」再 final「5床李爷爷，下午开始咳嗽」。断言 `acceptsPcm()` 为 true。

`RetryAsrTest` 的假客户端总是失败。先保存原声，再 retry，断言对象键相同且仍然存在。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-asr,yl-nursing -am test -Dtest=DashScopePcmTest,RetryAsrTest`
Expected: FAIL。现有引擎在有文本时会发 final，且没有 retry 接口。

- [ ] **Step 3: Write minimal implementation**

删除 `DashScopeStreamingAsrEngine.streamText` 里「有终稿文本就发 final」的分支。`open` 在密钥空白时只发 error。否则委托 `DashScopePcmClient`。生产 client 使用 DashScope Java SDK 的实时识别，模型名固定 `paraformer-realtime-v2`，采样率 16000，格式 pcm。

`RetryAsrClient` 从 `ObjectStore.get` 读出 PCM 再交给语音服务的 HTTP 或进程内端口。失败只更新记录状态，不调用 `delete`。

在 `yl-asr/pom.xml` 增加 DashScope SDK 依赖 `com.alibaba:dashscope-sdk-java`，版本 `2.18.2`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-asr,yl-nursing -am test -Dtest=DashScopePcmTest,RetryAsrTest,AsrApiTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-asr/pom.xml backend/yl-asr/src/main/java/com/ylcare/asr/engine/DashScopePcmClient.java backend/yl-asr/src/main/java/com/ylcare/asr/engine/DashScopeStreamingAsrEngine.java backend/yl-asr/src/test/java/com/ylcare/asr/engine/DashScopePcmTest.java backend/yl-nursing/src/main/java/com/ylcare/nursing/service/RetryAsrClient.java backend/yl-nursing/src/main/java/com/ylcare/nursing/web/OriginalAudioController.java backend/yl-nursing/src/test/java/com/ylcare/nursing/RetryAsrTest.java
git commit -m "feat: DashScope 只推 PCM，失败时保留原声键"
```

### Task 11: 识别 WebSocket 校验会话

**Files:**
- Create: `backend/yl-common/src/main/java/com/ylcare/common/SessionTokens.java`
- Modify: `backend/yl-asr/src/main/java/com/ylcare/asr/ws/AsrWebSocketHandler.java`
- Modify: `backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/AuthController.java`
- Test: `backend/yl-asr/src/test/java/com/ylcare/asr/ws/AsrSessionTest.java`

**Interfaces:**
- Consumes: `SessionTokens.staffId(String token)`，无效时 empty
- Produces: 握手头 `Authorization: Bearer {token}` 无效时关闭 WebSocket，close code 4401。有效时才接受二进制帧。护理服务新增 `GET /api/auth/session`，供语音服务的 HTTP 实现调用；测试里 `SessionTokens` 是内存假实现。

- [ ] **Step 1: Write the failing test**

用 Spring `WebSocketHandler` 直接调用 `afterConnectionEstablished`。无 Authorization 时期望会话被关闭且 code 为 4401。Bearer 为假实现认可的令牌时，状态表里有该 session，不关闭。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-asr -am test -Dtest=AsrSessionTest`
Expected: FAIL。现在无令牌也会建立状态。

- [ ] **Step 3: Write minimal implementation**

`AsrWebSocketHandler` 构造器增加 `SessionTokens`。`afterConnectionEstablished` 读取握手头。空的 staffId 时 `session.close(new CloseStatus(4401, "SESSION_EXPIRED"))` 并且不放入 `states`。

生产 `SessionTokens` 用 `RestClient` 调用护理服务 `GET /api/auth/session`，带同一个 Bearer。该接口本身必须允许携带令牌并通过拦截器，返回 `{ok:true,data:{staffId}}`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-asr,yl-nursing -am test -Dtest=AsrSessionTest,AuthLoginTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-common/src/main/java/com/ylcare/common/SessionTokens.java backend/yl-asr/src/main/java/com/ylcare/asr/ws/AsrWebSocketHandler.java backend/yl-nursing/src/main/java/com/ylcare/nursing/auth/AuthController.java backend/yl-asr/src/test/java/com/ylcare/asr/ws/AsrSessionTest.java
git commit -m "feat: 识别通道拒绝过期会话"
```

### Task 12: 交班提醒

**Files:**
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/notify/HandoverReminder.java`
- Create: `backend/yl-nursing/src/main/java/com/ylcare/nursing/notify/SubscribeMessageClient.java`
- Test: `backend/yl-nursing/src/test/java/com/ylcare/nursing/notify/HandoverReminderTest.java`

**Interfaces:**
- Consumes: `ShiftService` 的当天排班，`WECHAT_SUBSCRIBE_TEMPLATE_ID`
- Produces: `HandoverReminder.tick(ZonedDateTime now)`。当某班次结束时间减去现在小于等于 30 分钟且大于 0，并且该人员本班清单未提交时，调用 `SubscribeMessageClient.send(openId, shiftName, "距下班还有 30 分钟，请完成交接")` 一次。模板 id 为空时不调用 client。同一班次同一人只发送一次。

- [ ] **Step 1: Write the failing test**

白班结束 20:00。`tick` 在 19:40 对未提交且有 openId 的护理员发送一次。模板 id 为空时 client 调用次数为 0。19:40 再 tick 一次仍然只发送一次。

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-nursing -am test -Dtest=HandoverReminderTest`
Expected: FAIL，类不存在。

- [ ] **Step 3: Write minimal implementation**

`HandoverReminder` 用 `@Scheduled(fixedDelay = 60000)` 调用 `tick(ZonedDateTime.now(ZoneId.of("Asia/Shanghai")))`。已发送记录存在表 `reminder_sent(staffId, shiftDate, shiftName)`。`SubscribeMessageClient` 的生产实现在模板 id 非空时调用微信 `subscribeMessage.send`。测试注入记录调用次数的假客户端。

给 `yl-nursing` 主类加上 `@EnableScheduling`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-nursing -am test -Dtest=HandoverReminderTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-nursing/src/main/java/com/ylcare/nursing/notify backend/yl-nursing/src/main/java/com/ylcare/nursing/NursingApplication.java backend/yl-nursing/src/test/java/com/ylcare/nursing/notify/HandoverReminderTest.java
git commit -m "feat: 下班前三十分钟提醒交班"
```

### Task 13: 网关转发登录

**Files:**
- Modify: `backend/yl-gateway/src/main/resources/application.yml`
- Test: `backend/yl-gateway/src/test/java/com/ylcare/gateway/GatewayRoutesTest.java`

**Interfaces:**
- Consumes: 无
- Produces: 路由 `auth-api`，`Path=/api/auth/**`，`uri=lb://yl-nursing`，放在 `nursing-api` 之前。

- [ ] **Step 1: Write the failing test**

```java
@Test
void authPathIsRoutedToNursing() throws Exception {
    String yaml = Files.readString(Path.of("src/main/resources/application.yml"));
    assertTrue(yaml.contains("id: auth-api"));
    assertTrue(yaml.contains("Path=/api/auth/**"));
    assertTrue(yaml.indexOf("id: auth-api") < yaml.indexOf("id: nursing-api"));
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -B -pl yl-gateway -am test -Dtest=GatewayRoutesTest`
Expected: FAIL，找不到 `id: auth-api`。

- [ ] **Step 3: Write minimal implementation**

在 `routes` 列表最前面加入：

```yaml
- id: auth-api
  uri: lb://yl-nursing
  predicates:
    - Path=/api/auth/**
```

不要改动 `/ws/asr/**` 与 `/api/nursing/**`。

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -B -pl yl-gateway -am test -Dtest=GatewayRoutesTest,GatewayApplicationTest`
Expected: PASS。

- [ ] **Step 5: Commit**

```bash
git add backend/yl-gateway/src/main/resources/application.yml backend/yl-gateway/src/test/java/com/ylcare/gateway/GatewayRoutesTest.java
git commit -m "feat: 网关把登录请求转到护理服务"
```

### Task 14: 护理员小程序页面

**Files:**
- Create: `miniprogram/app.json`
- Create: `miniprogram/app.js`
- Create: `miniprogram/pages/gate/gate.js`
- Create: `miniprogram/pages/gate/gate.wxml`
- Create: `miniprogram/pages/home/home.js`
- Create: `miniprogram/pages/home/home.wxml`
- Create: `miniprogram/pages/record/record.js`
- Create: `miniprogram/pages/record/record.wxml`
- Create: `miniprogram/pages/preview/preview.js`
- Create: `miniprogram/pages/preview/preview.wxml`
- Create: `miniprogram/pages/detail/detail.js`
- Create: `miniprogram/pages/detail/detail.wxml`
- Create: `miniprogram/pages/handover/handover.js`
- Create: `miniprogram/pages/handover/handover.wxml`
- Create: `miniprogram/utils/api.js`
- Create: `miniprogram/手工测试.md`
- Test: `miniprogram/utils/home-buttons.test.js`

**Interfaces:**
- Consumes: Task 1 到 Task 12 的 HTTP 路径与字段名
- Produces: `homeButtons(home)` 返回 `{record, handover, confirm, confirmReason}`。`confirm` 恒为 false，`confirmReason` 恒为「接班确认下一期开放」。录音页使用 `wx.getRecorderManager`，`format: "pcm"`，`sampleRate: 16000`，`numberOfChannels: 1`，`frameSize: 4`。松手后若 `duration < 1000` 不调用上传。否则先 `POST /api/nursing/original-audio`。

- [ ] **Step 1: Write the failing test**

`home-buttons.test.js` 用 Node 内置 `node:test`：

```javascript
const test = require("node:test");
const assert = require("node:assert/strict");
const { homeButtons } = require("./home-state");

test("confirm stays closed", () => {
  const buttons = homeButtons({ recordEnabled: true, handoverEnabled: true });
  assert.equal(buttons.confirm, false);
  assert.equal(buttons.confirmReason, "接班确认下一期开放");
});
```

把纯函数放在 `miniprogram/utils/home-state.js`，页面只调用它。

- [ ] **Step 2: Run test to verify it fails**

Run: `node --test miniprogram/utils/home-buttons.test.js`
Expected: FAIL，找不到模块。

- [ ] **Step 3: Write minimal implementation**

`app.json` 的 pages 顺序为 gate、home、record、preview、detail、handover。`app.js` 启动时 `wx.login`，把 code 交给 `/api/auth/wechat`。`UNBOUND` 或 `DISABLED` 进入 gate 页并显示接口返回的 message。`admin` 与 `head_nurse` 也进入 gate，显示「院长端下一期开放」。

home 页请求 `/api/nursing/me/home`。三个按钮标题为记录、交接、确认。确认按钮 `disabled`。未排班时记录和交接也 disabled，并显示「联系管理员排班」。若响应含 `reminder` 文本，显示在姓名下方。正文样式 18px，姓名和按钮 22px。

record 页按住 `touchstart` 开始录音，`onFrameRecorded` 把 `frameBuffer` 通过已连接的 `wx.connectSocket` 发到 `/ws/asr`，URL 带不了自定义头时，在握手 header 里设置 `Authorization`。`onMessage` 把 partial 文本写到页面上。`touchend` 时若时长小于 1000 毫秒，停止录音并不上传，toast「再说一次」。否则停止后把临时文件上传 `original-audio`。上传失败把临时路径留在 `app.globalData.pendingAudio`，并提供「再传」按钮。上传成功后进入 preview。满 60 秒在 `onStop` 里同样进入上传。

preview 页展示原文 textarea、播放按钮（先请求签名 URL）、异常字段橙色。没有 `elderlyId` 时显示在住老人选择器，未选时确认按钮 disabled。确认调用创建记录和 `POST /api/nursing/records/{id}/confirm`。异常记录显示拍照和录像，录像 `maxDuration: 15`。重录返回 record 页。识别失败文案固定为「原声已保存，文字识别失败，可重试识别或重录」，并调用 retry-asr。

handover 页展示四段。提交调用 `POST /api/nursing/handovers/{id}/submit`。若返回 `SUPPLY_UNACKED`，先让用户确认无异常，再调用 supply-ack 后重试提交。已提交时隐藏提交按钮。

`手工测试.md` 列出 Android 微信和 iOS 微信各一轮：登录、未绑定、录音出字、识别失败仍能听原声、确认、交班锁定。文件开头写明：未提供 `WECHAT_APPID`、`WECHAT_SECRET`、`DASHSCOPE_API_KEY` 和 OSS 密钥时，本清单不算通过。

- [ ] **Step 4: Run test to verify it passes**

Run: `node --test miniprogram/utils/home-buttons.test.js`
Expected: PASS。

再跑后端全量：

Run: `mvn -B -pl yl-common,yl-gateway,yl-asr,yl-nursing -am test`
Expected: BUILD SUCCESS。

- [ ] **Step 5: Commit**

```bash
git add miniprogram
git commit -m "feat: 护理员微信小程序走通记录和交班"
```
