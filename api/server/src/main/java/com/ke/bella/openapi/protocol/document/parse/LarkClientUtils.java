package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.common.exception.ChannelException;
import com.lark.oapi.Client;
import com.lark.oapi.service.docx.v1.model.Image;
import com.lark.oapi.service.drive.v1.model.CreateImportTaskReq;
import com.lark.oapi.service.drive.v1.model.CreateImportTaskResp;
import com.lark.oapi.service.drive.v1.model.DeleteFileReq;
import com.lark.oapi.service.drive.v1.model.DeleteFileResp;
import com.lark.oapi.service.drive.v1.model.DownloadMediaReq;
import com.lark.oapi.service.drive.v1.model.DownloadMediaResp;
import com.lark.oapi.service.drive.v1.model.GetImportTaskReq;
import com.lark.oapi.service.drive.v1.model.GetImportTaskResp;
import com.lark.oapi.service.drive.v1.model.ImportTask;
import com.lark.oapi.service.drive.v1.model.ImportTaskMountPoint;
import com.lark.oapi.service.drive.v1.model.UploadAllFileReq;
import com.lark.oapi.service.drive.v1.model.UploadAllFileReqBody;
import com.lark.oapi.service.drive.v1.model.UploadAllFileResp;
import com.theokanning.openai.service.FileUtil;
import lombok.extern.slf4j.Slf4j;
import okhttp3.MediaType;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.util.Base64;

@Slf4j
public class LarkClientUtils {

    public static String uploadFile(Client client, String fileName, String dirToken, File file) {
        try {
            UploadAllFileReq req = UploadAllFileReq.newBuilder()
                    .uploadAllFileReqBody(UploadAllFileReqBody.newBuilder()
                            .fileName(fileName)
                            .file(file)
                            .parentType("explorer")
                            .parentNode(dirToken)
                            .size((int) file.length())
                            .build())
                    .build();
            UploadAllFileResp resp = client.drive().v1().file().uploadAll(req);
            if(resp.getCode() != 0) {
                throw new ChannelException.OpenAIException(502, "channel_error", resp.getMsg());
            }
            return resp.getData().getFileToken();
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

    public static String importTask(Client client, String fileToken, String dirToken, String fileName, String fileType) {
        CreateImportTaskReq req = CreateImportTaskReq.newBuilder()
                .importTask(ImportTask.newBuilder()
                        .fileExtension(fileType)
                        .fileToken(fileToken)
                        .type(fileType)
                        .fileName(fileName)
                        .point(ImportTaskMountPoint.newBuilder()
                                .mountType(1)
                                .mountKey(dirToken)
                                .build())
                        .build())
                .build();
        try {
            CreateImportTaskResp resp = client.drive().v1().importTask().create(req);
            if(resp.getCode() != 0) {
                throw new ChannelException.OpenAIException(502, "channel_error", resp.getMsg());
            }
            return resp.getData().getTicket();
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

    public static DocParseResponse queryTaskResult(Client client, String ticket) {
        GetImportTaskReq req = GetImportTaskReq.newBuilder()
                .ticket(ticket)
                .build();
        try {
            GetImportTaskResp resp = client.drive().v1().importTask().get(req);
            if(resp.getCode() != 0) {
                throw new ChannelException.OpenAIException(502, "channel_error", resp.getMsg());
            }
            DocParseResponse response = new DocParseResponse();
            switch (resp.getData().getResult().getJobStatus()) {
            case 0:
                response.setToken(resp.getData().getResult().getToken());
                response.setStatus("success");
                break;
            case 2:
                response.setStatus("processing");
                break;
            default:
                response.setStatus("failed");
                response.setMessage(resp.getData().getResult().getJobErrorMsg());
            }
            return response;
        } catch (Exception e) {
            throw ChannelException.fromException(e);
        }
    }

    /**
     * 删除文件
     * 
     * @param client    Lark客户端
     * @param fileToken 文件token
     * @param fileType  文件类型
     * 
     * @return 删除是否成功
     */
    public static boolean deleteFile(Client client, String fileToken, String fileType) {
        try {
            DeleteFileReq req = DeleteFileReq.newBuilder()
                    .fileToken(fileToken)
                    .type(fileType)
                    .build();

            DeleteFileResp resp = client.drive().v1().file().delete(req);
            if(resp.getCode() != 0) {
                log.error("Failed to delete file {}: {}", fileToken, resp.getMsg());
                return false;
            }
            log.info("Successfully deleted file: {}", fileToken);
            return true;
        } catch (Exception e) {
            log.error("Exception when deleting file {}: {}", fileToken, e.getMessage(), e);
            return false;
        }
    }

    /**
     * 获取图片
     * 
     * @param imageBlock 图片Block对象
     * @param client     LarkClient
     * 
     * @return 图片Base64String
     */
    public static String getImageUrl(Image imageBlock, Client client) {
        DownloadMediaReq req = DownloadMediaReq.newBuilder()
                .fileToken(imageBlock.getToken())
                .build();
        try {
            DownloadMediaResp resp = client.drive().v1().media().download(req);
            if(resp.getCode() != 0) {
                log.warn(resp.getMsg());
                return "";
            }
            ByteArrayOutputStream stream = resp.getData();
            String fileName = resp.getFileName();
            MediaType mimeType = FileUtil.getFileUploadMediaType(fileName);
            return "data:" + mimeType + ";base64," + Base64.getEncoder().encodeToString(stream.toByteArray());
        } catch (Exception e) {
            log.warn(e.getMessage(), e);
            return "";
        }
    }
}
