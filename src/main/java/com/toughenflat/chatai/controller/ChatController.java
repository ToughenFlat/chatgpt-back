package com.toughenflat.chatai.controller;

import com.google.common.collect.ImmutableList;
import com.toughenflat.chatai.api.baidu.constant.BaiDuConst;
import com.toughenflat.chatai.api.openai.ChatGPTApi;
import com.toughenflat.chatai.api.openai.enums.Role;
import com.toughenflat.chatai.api.openai.req.ContextMessage;
import com.toughenflat.chatai.entity.UserApiKeyEntity;
import com.toughenflat.chatai.service.*;
import com.toughenflat.chatai.service.helper.ExpertChatHelper;
import org.springframework.web.bind.annotation.*;
import com.alibaba.fastjson.JSON;
import com.toughenflat.chatai.api.openai.constant.OpenAIConst;
import com.toughenflat.chatai.api.openai.req.ChatGPTReq;
import com.toughenflat.chatai.api.openai.resp.ChatGPTResp;
import com.toughenflat.chatai.api.openai.resp.CreditGrantsResp;
import com.toughenflat.chatai.dto.AddSessionRequest;
import com.toughenflat.chatai.dto.OneShotChatRequest;
import com.toughenflat.chatai.dto.RenameSessionRequest;
import com.toughenflat.chatai.dto.SessionChatRequest;
import com.toughenflat.chatai.entity.SessionChatRecordEntity;
import com.toughenflat.chatai.entity.UserSessionEntity;
import com.toughenflat.chatai.enums.ApiType;
import com.toughenflat.chatai.enums.SessionType;
import com.toughenflat.chatai.utils.OkHttpClientUtil;
import com.toughenflat.chatai.utils.ResultCode;
import com.toughenflat.chatai.utils.ReturnResult;
import com.toughenflat.chatai.api.baidu.util.VoiceTool;
import okhttp3.*;

import org.springframework.util.CollectionUtils;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.multipart.MultipartFile;

import javax.annotation.Resource;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.text.DateFormat;

import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;


/**
 * @Author: huangpenglong
 * @Date: 2023/3/9 15:44
 */

@RestController
@RequestMapping()
@CrossOrigin
public class ChatController {
    private static final String NAME_MESSAGE = "message";
    private static final String CONTENT_TYPE_JSON = "application/json";

    @Resource
    private ChatService chatService;

    @Resource
    private UserSessionService userSessionService;

    @Resource
    private SessionChatRecordService sessionChatRecordService;

    @Resource
    private FileService fileService;

    @Resource
    private AdminApiKeyService adminApiKeyService;

    @Resource
    private UserApiKeyService userApiKeyService;

    @Resource
    private ExpertChatHelper expertChatHelper;

    /**
     * 调用openai的ChatGPT接口实现单论对话
     */
    @PostMapping("/chat/oneShot")
    public ReturnResult oneShot(@RequestBody @Valid OneShotChatRequest req) {
        if (StringUtils.isEmpty(req.getMessage()) || req.getUserId() == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }

        // 若用户上传了apikey则使用用户的，否则采用本系统的
        UserApiKeyEntity userApiKeyEntity = userApiKeyService.getByUserIdAndType(req.getUserId(), ApiType.OPENAI);
        String apiKey = userApiKeyEntity != null && !StringUtils.isEmpty(userApiKeyEntity.getApikey())
                ? userApiKeyEntity.getApikey() : adminApiKeyService.roundRobinGetByType(ApiType.OPENAI);
        if (apiKey == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.ADMIN_APIKEY_NULL);
        }
        SessionType sessionType = SessionType.get(req.getSessionType());
        ChatGPTReq gptReq = ChatGPTReq.builder()
                .model(OpenAIConst.MODEL_NAME_CHATGPT_3_5)
                .messages(ImmutableList.of(new ContextMessage(Role.USER.name, req.getMessage())))
                .max_tokens(OpenAIConst.MAX_TOKENS - sessionType.maxContextToken)
                .build();
        ChatGPTResp resp = chatService.oneShotChat(req.getUserId(), gptReq, apiKey);

        if (resp == null) {
            return ReturnResult.error();
        }
        return ReturnResult.ok().data(NAME_MESSAGE, resp.getMessage());
    }


    /**
     * 创建一个会话
     * 会话类型见SessionType
     *
     * @param req
     * @return
     */
    @PostMapping("/chat/addSession")
    public ReturnResult addSession(@RequestBody @Valid AddSessionRequest req) {
        if (StringUtils.isEmpty(req.getSessionName()) || req.getUserId() == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }
        SessionType type = SessionType.get(req.getType());
        if (type == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.BAD_PARAM);
        }

        // 创建会话
        UserSessionEntity userSessionEntity = userSessionService.save(req.getUserId(), req.getSessionName(), type);

        // 若会话是专家领域创建会话后插入一条提示记录
        if (userSessionEntity != null && SessionType.EXPERT_CHAT.equals(type)) {
            expertChatHelper.handleSessionSystemRecord(userSessionEntity);
        }
        return userSessionEntity != null ? ReturnResult.ok() : ReturnResult.error();
    }

    /**
     * 修改会话名
     */
    @PostMapping("/chat/renameSession")
    public ReturnResult addSession(@RequestBody @Valid RenameSessionRequest req) {
        if (StringUtils.isEmpty(req.getSessionName()) || req.getSessionId() == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }
        return userSessionService.updateById(new UserSessionEntity(req.getSessionId(), req.getSessionName()))
                ? ReturnResult.ok() : ReturnResult.error();
    }

    /**
     * 调用openai的ChatGPT接口实现多轮对话
     */
    @PostMapping("/chat/session")
    public ReturnResult chatSession(@RequestBody @Valid SessionChatRequest req) {
        if (StringUtils.isEmpty(req.getMessage()) || req.getUserId() == null || req.getSessionId() == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }

        // 若用户上传了apikey则使用用户的，否则采用本系统的
        UserApiKeyEntity userApiKeyEntity = userApiKeyService.getByUserIdAndType(req.getUserId(), ApiType.OPENAI);
        String apiKey = userApiKeyEntity != null && !StringUtils.isEmpty(userApiKeyEntity.getApikey())
                ? userApiKeyEntity.getApikey()
                : adminApiKeyService.roundRobinGetByType(ApiType.OPENAI);
        if (apiKey == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.ADMIN_APIKEY_NULL);
        }

        SessionType sessionType = SessionType.get(req.getSessionType());
        ChatGPTReq gptReq = ChatGPTReq.builder()
                .model(OpenAIConst.MODEL_NAME_CHATGPT_3_5)
                .max_tokens(OpenAIConst.MAX_TOKENS - sessionType.maxContextToken)
                .build();

        ChatGPTResp resp = chatService.sessionChat(
                req.getUserId(), req.getSessionId(), gptReq, req.getMessage(), apiKey, sessionType);
        if (resp == null) {
            return ReturnResult.error();
        }
        return ReturnResult.ok().data(NAME_MESSAGE, resp.getMessage());
    }

    /**
     * 获取用户的会话列表
     */
    @GetMapping("/chat/getSessionList/{userId}/{type}")
    public ReturnResult getSessionList(@PathVariable String userId, @PathVariable Integer type) {
        SessionType sessionType = SessionType.get(type);
        if (sessionType == null) {
            return ReturnResult.error();
        }
        return ReturnResult.ok().data(
                "list", userSessionService.getSessionList(userId, sessionType));
    }

    /**
     * 获取某个会话的聊天记录
     */
    @GetMapping("/chat/getSessionChatRecord/{sessionId}")
    public ReturnResult getSessionChatRecord(@PathVariable Integer sessionId) {
        return ReturnResult.ok().data("record", sessionChatRecordService.getSessionRecord(sessionId));
    }

    /**
     * 清空会话的聊天记录
     */
    @PutMapping("/chat/truncateSessionChatRecord/{sessionId}")
    public ReturnResult truncateSessionChatRecord(@PathVariable Integer sessionId) {
        if (sessionId == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }

        sessionChatRecordService.truncateSessionChatRecord(sessionId);
        chatService.refreshWindowRecordCache(sessionId);
        return ReturnResult.ok();
    }

    /**
     * 删除会话
     */
    @DeleteMapping("/chat/deleteSession/{sessionId}")
    public ReturnResult deleteSession(@PathVariable Integer sessionId) {
        if (sessionId == null) {
            return ReturnResult.error().codeAndMessage(ResultCode.EMPTY_PARAM);
        }

        userSessionService.removeById(sessionId);
        sessionChatRecordService.truncateSessionChatRecord(sessionId);
        chatService.refreshWindowRecordCache(sessionId);
        return ReturnResult.ok();
    }

    /**
     * 查询openai apikey的余额
     */
    @GetMapping("/chat/getCreditGrants/{apiKey}")
    public ReturnResult getCreditGrants(@PathVariable String apiKey) {
        CreditGrantsResp openAiCreditGrantsResp = chatService.creditGrants(apiKey);
        return ReturnResult.ok().data("grant", openAiCreditGrantsResp.getGrants().getActualData());
    }

    /**
     * 把当前会话的聊天记录输出为csv文件
     */
    @GetMapping(value = "/chat/getSessionChatRecordByCsv/{sessionId}")
    public ReturnResult getSessionChatRecordByCsv(@PathVariable Integer sessionId, HttpServletResponse response) {

        // 获取聊天记录
        List<SessionChatRecordEntity> sessionRecord = sessionChatRecordService.getSessionRecord(sessionId);

        // 转换成csv文件
        String[] titleRow = {"角色", "内容", "token数量", "创建时间", "更新时间"};
        List<String[]> sessionList = null;
        if (!CollectionUtils.isEmpty(sessionRecord)) {
            sessionList = sessionRecord.stream()
                    .map(record -> new String[]{
                            record.getRole(),
                            record.getContent(),
                            record.getTokenNum().toString(),
                            DateFormat.getDateTimeInstance().format(record.getCreateTime()),
                            DateFormat.getDateTimeInstance().format(record.getUpdateTime())})
                    .collect(Collectors.toList());

        }
        fileService.exportCsv(titleRow, sessionList, response);
        return ReturnResult.ok();
    }

    /**
     * 通过语音文件获取文字
     */
    @PostMapping("/chat/getTextByAudio")
    public ReturnResult getTextByAudio(@RequestBody MultipartFile file) throws IOException {
        byte[] bytes = file.getBytes();
        String response = VoiceTool.SpeechRecognition(bytes);
        return ReturnResult.ok().message(response);
    }

    /**
     * 百度语音识别权限认证
     */
    @PostMapping("/chat/getAudioToken")
    public ReturnResult getAudioToken() throws IOException {
        OkHttpClient client = OkHttpClientUtil.getClient();
        okhttp3.RequestBody body = okhttp3.RequestBody.create(MediaType.parse(CONTENT_TYPE_JSON), "");
        Request request = new Request.Builder()
                .url("https://aip.baidubce.com/oauth/2.0/token?client_id=" + BaiDuConst.API_KEY + "&client_secret="
                        + BaiDuConst.SECRET_KEY + "&grant_type=client_credentials")
                .method("POST", body)
                .addHeader("Content-Type", CONTENT_TYPE_JSON)
                .addHeader("Accept", CONTENT_TYPE_JSON)
                .build();
        Response response = client.newCall(request).execute();
        return (response.body() == null)
                ? ReturnResult.error()
                : ReturnResult.ok().message(JSON.parseObject(response.body().string()).getString("access_token"));
    }
}
