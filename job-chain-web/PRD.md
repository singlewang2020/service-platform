# Job Chain Web PRD

## 1. 鑳屾櫙涓庣洰鏍?Job Chain Web 鐢ㄤ簬绠＄悊 Job 涓?Job Chain锛圖AG锛夛紝骞舵煡鐪嬭繍琛岀姸鎬併€傚墠绔紭鍏堟弧瓒崇鐞嗕笌鍙鍖栭渶姹傦紝鏀寔鍚庣画鎺ュ叆 job-chain-dag 鐨?OpenAPI銆?
鐩爣锛?- 鎻愪緵缁熶竴鐨?Job/Chain 绠＄悊鍏ュ彛銆?- 鍙彂璧疯繍琛屻€佹煡鐪嬭繍琛岀姸鎬佷笌鑺傜偣鎵ц鎯呭喌銆?- 鎻愪緵娓呮櫚鐨勭櫥褰曞叆鍙ｏ紙鍓嶇纭紪鐮佽处鍙凤級銆?
## 2. 鐩爣鐢ㄦ埛
- 杩愮淮/骞冲彴绠＄悊鍛?- 浠诲姟缂栨帓宸ョ▼甯?
## 3. 淇℃伅鏋舵瀯
椤甸潰缁撴瀯锛?- 鐧诲綍椤?- 鎺у埗鍙帮紙Dashboard锛?- Jobs
- Chains
- Runs
- 璁剧疆/甯姪锛堝彲閫夛級

## 4. 鍔熻兘闇€姹?### 4.1 鐧诲綍
- 璐﹀彿/瀵嗙爜纭紪鐮侊細`admin/123456`
- 鐧诲綍鎴愬姛鍚庤繘鍏ユ帶鍒跺彴
- 鏀寔璁颁綇鐧诲綍鐘舵€侊紙localStorage锛?
### 4.2 Jobs
API 鍙傝€冿紙job-chain-dag OpenAPI锛夛細
- `GET /api/v1/jobs`锛氬垎椤垫煡璇?- `POST /api/v1/jobs`锛氬垱寤?- `PUT /api/v1/jobs/{jobId}`锛氭洿鏂?- `DELETE /api/v1/jobs/{jobId}`锛氬垹闄?- `POST /api/v1/jobs/{jobId}:enable`锛氬惎鐢?- `POST /api/v1/jobs/{jobId}:disable`锛氬仠鐢?- `POST /api/v1/jobs/{jobId}:start`锛氬惎鍔ㄨ繍琛?
鍓嶇鍔熻兘锛?- 鍒楄〃銆佺瓫閫夛紙keyword/enabled锛?- 鏂板缓/缂栬緫 Job锛坣ame/description/type/configJson/enabled锛?- 鍚敤/鍋滅敤銆佸垹闄ゃ€佸惎鍔ㄨ繍琛?
### 4.3 Chains
API 鍙傝€冿細
- `GET /api/v1/chains`
- `POST /api/v1/chains`
- `PUT /api/v1/chains/{chainId}`
- `POST /api/v1/chains/{chainId}:enable`
- `POST /api/v1/chains/{chainId}:disable`
- `POST /api/v1/chains/{chainId}:start`
- `GET /api/v1/chains/{chainId}`

鍓嶇鍔熻兘锛?- 鍒楄〃銆佺瓫閫夛紙keyword/enabled锛?- 鏂板缓/缂栬緫 Chain锛坣ame/description/dagJson/enabled/version锛?- 鍚敤/鍋滅敤銆佸惎鍔ㄨ繍琛?- 鍙瑙?DAG JSON

### 4.4 Runs
API 鍙傝€冿細
- `GET /api/v1/runs/{runId}`锛氳繍琛岃鎯?- `GET /api/v1/runs/{runId}/nodes`锛氳妭鐐瑰垪琛?- `POST /api/v1/runs/{runId}:stop`锛氬仠姝㈣繍琛?- `POST /api/v1/runs/{runId}/nodes/{nodeId}:retry`锛氶噸璇曡妭鐐?- `POST /api/v1/runs/{runId}/nodes/{nodeId}:complete`锛氭墜鍔ㄥ畬鎴愯妭鐐?
鍓嶇鍔熻兘锛?- 杩愯鍒楄〃锛堝熀浜庡惎鍔ㄨ繑鍥炴垨鎼滅储锛?- 杩愯璇︽儏涓庤妭鐐规墽琛岀姸鎬佸睍绀?- 鎿嶄綔锛氬仠姝㈣繍琛屻€佽妭鐐归噸璇?瀹屾垚

## 5. 浜や簰涓庣姸鎬?- 鍚勫垪琛ㄦ敮鎸佺┖鎬佷笌鍔犺浇鎬?- 鍏抽敭鎿嶄綔鎻愮ず锛堟垚鍔?澶辫触锛?- 琛ㄥ崟鏍￠獙锛氬繀濉瓧娈甸珮浜彁绀?
## 6. 闈炲姛鑳介渶姹?- 鍝嶅簲寮忓竷灞€锛堟闈?绉诲姩锛?- 缁熶竴瑙嗚涓庨鏍肩郴缁燂紙棰滆壊銆佸瓧浣撱€侀棿璺濓級
- 鍔ㄦ晥锛氶〉闈㈠姞杞芥贰鍏ャ€佸垪琛ㄦ笎鏄?
## 7. 閲岀▼纰?- M1锛氬畬鎴愮櫥褰曘€佸熀纭€瀵艰埅涓庨〉闈㈤鏋?- M2锛氬畬鎴?Jobs/Chains CRUD UI 涓庣姸鎬佹帶鍒?- M3锛氬畬鎴?Runs 璇︽儏涓庤妭鐐规搷浣?UI


