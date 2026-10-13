package com.ke.bella.openapi.protocol.document.parse;

import com.ke.bella.openapi.common.exception.BizParamCheckException;
import com.ke.bella.openapi.common.exception.BellaException;
import com.ke.bella.openapi.server.OpenAiServiceFactory;
import com.lark.oapi.Client;
import com.lark.oapi.service.docx.v1.model.Block;
import com.lark.oapi.service.docx.v1.model.Image;
import com.lark.oapi.service.docx.v1.model.ListDocumentBlockReq;
import com.lark.oapi.service.docx.v1.model.ListDocumentBlockResp;
import com.lark.oapi.service.docx.v1.model.ListDocumentBlockRespBody;
import com.lark.oapi.service.docx.v1.model.TableMergeInfo;
import com.lark.oapi.service.docx.v1.model.TextElement;
import com.theokanning.openai.service.FileUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.deleteFile;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.getImageUrl;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.importTask;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.queryTaskResult;
import static com.ke.bella.openapi.protocol.document.parse.LarkClientUtils.uploadFile;

@Slf4j
@Component("LarkDocumentParse")
public class LarkAdaptor implements DocParseAdaptor<LarkProperty> {
    @Autowired
    private LarkFileCleanupService cleanupService;

    @Autowired
    private OpenAiServiceFactory openAiServiceFactory;

    @Override
    public DocParseTaskInfo doParse(DocParseRequest request, String url, String channelCode, LarkProperty property) {
        try {
            SourceFile sourceFile = request.getFile();
            String dotFileType = FileUtil.getFileExtension(sourceFile.getName());
            String fileType = dotFileType.isEmpty() ? dotFileType : dotFileType.substring(1);
            if(property.getSupportTypes() != null && Arrays.stream(property.getSupportTypes()).noneMatch(support -> support.equals(fileType))) {
                throw new BizParamCheckException("File type must be any one of: " + String.join(",", property.getSupportTypes()));
            }
            File tempFile = File.createTempFile("lark_", sourceFile.getName());
            openAiServiceFactory.create().retrieveFileContentAndSave(sourceFile.getId(), tempFile.getPath());
            Client client = LarkClientProvider.client(property.getClientId(), property.getClientSecret());
            String fileToken = uploadFile(client, sourceFile.getName(), property.getUploadDirToken(), tempFile);
            String ticket = importTask(client, fileToken, property.getCloudDirToken(), sourceFile.getName(), fileType);

            // 注册文件清理任务
            cleanupService.addCleanupTask(fileToken, ticket, property);

            return DocParseTaskInfo.builder()
                    .taskId(TaskIdUtils.buildTaskId(channelCode, ticket))
                    .build();
        } catch (Exception e) {
            throw BellaException.fromException(e);
        }
    }

    @Override
    public DocParseResponse queryResult(String taskId, String url, LarkProperty property) {
        Client client = LarkClientProvider.client(property.getClientId(), property.getClientSecret());
        DocParseResponse response = queryTaskResult(client, taskId);
        if("success".equals(response.getStatus())) {
            DocParseResult result = getDocParseResult(client, response.getToken());
            response.setResult(result);
        }
        response.setCallback(() -> deleteFile(client, response.getToken(), "docx"));
        return response;
    }

    @Override
    public boolean isCompletion(String taskId, String url, LarkProperty property) {
        Client client = LarkClientProvider.client(property.getClientId(), property.getClientSecret());
        DocParseResponse response = queryTaskResult(client, taskId);
        return "success".equals(response.getStatus()) || "failed".equals(response.getStatus());
    }

    @Override
    public String getDescription() {
        return "lark文档解析";
    }

    @Override
    public Class<?> getPropertyClass() {
        return LarkProperty.class;
    }

    private static DocParseResult getDocParseResult(Client client, String token) {
        List<Block> blocks = getBlocks(client, token, null);
        blocks = blocks.stream()
                .filter(block -> !emptyTitle(block))
                .collect(Collectors.toList());
        return convertTo(blocks, client);
    }

    private static boolean emptyTitle(Block block) {
        if(block.getBlockType() < 3 || block.getBlockType() > 11) {
            return false;
        }
        String text = null;
        switch (block.getBlockType()) {
        case 3:
            text = extractElementsText(block.getHeading1().getElements());
            break;
        case 4:
            text = extractElementsText(block.getHeading2().getElements());
            break;
        case 5:
            text = extractElementsText(block.getHeading3().getElements());
            break;
        case 6:
            text = extractElementsText(block.getHeading4().getElements());
            break;
        case 7:
            text = extractElementsText(block.getHeading5().getElements());
            break;
        case 8:
            text = extractElementsText(block.getHeading6().getElements());
            break;
        case 9:
            text = extractElementsText(block.getHeading7().getElements());
            break;
        case 10:
            text = extractElementsText(block.getHeading8().getElements());
            break;
        case 11:
            text = extractElementsText(block.getHeading9().getElements());
            break;
        }
        return StringUtils.isBlank(text);
    }

    private static List<Block> getBlocks(Client client, String token, String pageToken) {
        ListDocumentBlockReq req = ListDocumentBlockReq.newBuilder()
                .documentId(token)
                .pageSize(500)
                .pageToken(pageToken)
                .documentRevisionId(-1)
                .build();
        try {
            ListDocumentBlockResp resp = client.docx().v1().documentBlock().list(req);
            if(resp.getCode() != 0) {
                throw new BellaException.ChannelException(502, resp.getMsg());
            }
            ListDocumentBlockRespBody body = resp.getData();
            List<Block> blocks = new ArrayList<>(Arrays.asList(body.getItems()));
            if(body.getHasMore()) {
                blocks.addAll(getBlocks(client, token, body.getPageToken()));
            }
            return blocks;
        } catch (Exception e) {
            throw BellaException.fromException(e);
        }
    }

    /**
     * 将飞书Block列表转换为DocParseResult格式
     * 
     * @param blocks 飞书返回的Block列表
     * @param client LarkClient
     * 
     * @return 转换后的DocParseResult对象
     */
    private static DocParseResult convertTo(List<Block> blocks, Client client) {
        if(blocks == null || blocks.isEmpty()) {
            return null;
        }

        // 找到根节点（parent_id为空的节点）
        Block rootBlock = blocks.stream()
                .filter(block -> block.getParentId() == null || block.getParentId().isEmpty())
                .findFirst()
                .orElse(blocks.get(0));

        DocParseResult result = new DocParseResult();

        // 设置根节点信息
        result.setSummary("");
        result.setPath(null); // 根节点path为null
        result.setElement(null); // 根节点element为null

        // 获取所有属于根节点的直接子节点
        List<Block> rootChildren = blocks.stream()
                .filter(block -> rootBlock.getBlockId().equals(block.getParentId()))
                .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

        // 按标题层级构建树形结构
        result.setChildren(buildHierarchicalStructure(rootChildren, blocks, client));

        return result;
    }

    /**
     * 按标题层级构建树形结构
     * 
     * @param blocks    要处理的Block列表
     * @param allBlocks 所有Block列表（用于查找子节点）
     * @param client    LarkClient
     * 
     * @return 构建好的DocParseResult列表
     */
    private static List<DocParseResult> buildHierarchicalStructure(List<Block> blocks, List<Block> allBlocks, Client client) {
        List<DocParseResult> results = new ArrayList<>();

        for (int i = 0; i < blocks.size(); i++) {
            Block currentBlock = blocks.get(i);
            int currentLevel = getHeadingLevel(currentBlock);

            // 创建当前节点
            DocParseResult current = new DocParseResult();
            current.setSummary("");
            current.setPath(Arrays.asList(results.size() + 1)); // 路径从1开始
            current.setElement(createElement(currentBlock, allBlocks, client));

            // 如果是标题，查找属于该标题的内容
            if(currentLevel > 0) {
                List<Block> childBlocks = new ArrayList<>();

                // 找到下一个同级或更高级标题之前的所有内容
                for (int j = i + 1; j < blocks.size(); j++) {
                    Block nextBlock = blocks.get(j);
                    int nextLevel = getHeadingLevel(nextBlock);

                    // 如果遇到同级或更高级标题，停止
                    if(nextLevel > 0 && nextLevel <= currentLevel) {
                        break;
                    }

                    childBlocks.add(nextBlock);
                }

                // 递归构建子结构
                current.setChildren(buildHierarchicalStructure(childBlocks, allBlocks, client));

                // 更新路径
                updateChildrenPaths(current.getChildren(), current.getPath());

                // 跳过已处理的子节点
                i += childBlocks.size();
            } else {
                // 非标题节点，查找其直接子节点（基于parent_id）
                // 排除表格相关的block类型，因为它们已经在表格的rows中处理
                List<Block> directChildren = allBlocks.stream()
                        .filter(block -> currentBlock.getBlockId().equals(block.getParentId()))
                        .filter(block -> !isTableRelatedBlock(block)) // 排除表格相关block
                        .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);

                if(!directChildren.isEmpty()) {
                    current.setChildren(buildHierarchicalStructure(directChildren, allBlocks, client));
                    updateChildrenPaths(current.getChildren(), current.getPath());
                }
            }

            results.add(current);
        }

        return results;
    }

    /**
     * 判断是否为表格相关的block类型
     * 
     * @param block Block对象
     * 
     * @return 是否为表格相关block
     */
    private static boolean isTableRelatedBlock(Block block) {
        if(block == null)
            return false;
        return 32 == block.getBlockType();
    }

    /**
     * 判断Block是否包含复杂内容（非纯文本）
     * 
     * @param block Block对象
     * 
     * @return 是否为复杂内容
     */
    private static boolean isComplexBlock(Block block) {
        if(block == null)
            return false;

        switch (block.getBlockType()) {
        case 2: // text - 纯文本，不是复杂内容
            return false;
        case 27: // image - 图片是复杂内容
        case 23: // equation - 公式是复杂内容
        case 15: // code - 代码块是复杂内容
        case 31: // table - 嵌套表格是复杂内容
        case 12: // bullet - 列表项是复杂内容
        case 13: // ordered - 有序列表项是复杂内容
            return true;
        case 3:
        case 4:
        case 5:
        case 6:
        case 7:
        case 8:
        case 9:
        case 10:
        case 11: // 各级标题
            return true; // 标题在单元格中也算复杂内容
        default:
            return false;
        }
    }

    /**
     * 获取标题级别
     * 
     * @param block Block对象
     * 
     * @return 标题级别（1-9），非标题返回0
     */
    private static int getHeadingLevel(Block block) {
        if(block == null)
            return 0;

        switch (block.getBlockType()) {
        case 3:
            return 1; // heading1
        case 4:
            return 2; // heading2
        case 5:
            return 3; // heading3
        case 6:
            return 4; // heading4
        case 7:
            return 5; // heading5
        case 8:
            return 6; // heading6
        case 9:
            return 7; // heading7
        case 10:
            return 8; // heading8
        case 11:
            return 9; // heading9
        default:
            return 0; // 非标题
        }
    }

    /**
     * 更新子节点的路径
     * 
     * @param children   子节点列表
     * @param parentPath 父节点路径
     */
    private static void updateChildrenPaths(List<DocParseResult> children, List<Integer> parentPath) {
        if(children == null || children.isEmpty())
            return;

        for (int i = 0; i < children.size(); i++) {
            DocParseResult child = children.get(i);
            List<Integer> childPath = new ArrayList<>();
            if(parentPath != null) {
                childPath.addAll(parentPath);
            }
            childPath.add(i + 1); // 路径从1开始
            child.setPath(childPath);

            // 递归更新孙子节点
            updateChildrenPaths(child.getChildren(), childPath);
        }
    }

    /**
     * 根据Block创建Element对象
     * 
     * @param block     飞书Block对象
     * @param allBlocks 所有Block列表（用于查找表格子节点）
     * @param client    LarkClient
     * 
     * @return Element对象
     */
    private static DocParseResult.Element createElement(Block block, List<Block> allBlocks, Client client) {
        DocParseResult.Element element = new DocParseResult.Element();

        // 根据block_type设置类型和内容
        switch (block.getBlockType()) {
        case 1: // page
            element.setType("Text");
            if(block.getPage() != null) {
                element.setText(extractElementsText(block.getPage().getElements()));
            }
            break;
        case 2: // text
            element.setType("Text");
            if(block.getText() != null) {
                element.setText(extractElementsText(block.getText().getElements()));
            }
            break;
        case 3: // heading1
            element.setType("Title");
            if(block.getHeading1() != null) {
                element.setText(extractElementsText(block.getHeading1().getElements()));
            }
            break;
        case 4: // heading2
            element.setType("Title");
            if(block.getHeading2() != null) {
                element.setText(extractElementsText(block.getHeading2().getElements()));
            }
            break;
        case 5: // heading3
            element.setType("Title");
            if(block.getHeading3() != null) {
                element.setText(extractElementsText(block.getHeading3().getElements()));
            }
            break;
        case 6: // heading4
            element.setType("Title");
            if(block.getHeading4() != null) {
                element.setText(extractElementsText(block.getHeading4().getElements()));
            }
            break;
        case 7: // heading5
            element.setType("Title");
            if(block.getHeading5() != null) {
                element.setText(extractElementsText(block.getHeading5().getElements()));
            }
            break;
        case 8: // heading6
            element.setType("Title");
            if(block.getHeading6() != null) {
                element.setText(extractElementsText(block.getHeading6().getElements()));
            }
            break;
        case 9: // heading7
            element.setType("Title");
            if(block.getHeading7() != null) {
                element.setText(extractElementsText(block.getHeading7().getElements()));
            }
            break;
        case 10: // heading8
            element.setType("Title");
            if(block.getHeading8() != null) {
                element.setText(extractElementsText(block.getHeading8().getElements()));
            }
            break;
        case 11: // heading9
            element.setType("Title");
            if(block.getHeading9() != null) {
                element.setText(extractElementsText(block.getHeading9().getElements()));
            }
            break;
        case 12: // bullet
            element.setType("ListItem");
            if(block.getBullet() != null) {
                element.setText(extractElementsText(block.getBullet().getElements()));
            }
            break;
        case 13: // ordered
            element.setType("ListItem");
            if(block.getOrdered() != null) {
                element.setText(extractElementsText(block.getOrdered().getElements()));
            }
            break;
        case 15: // code
            element.setType("Code");
            if(block.getCode() != null) {
                element.setText(extractElementsText(block.getCode().getElements()));
            }
            break;
        case 23: // equation
            element.setType("Formula");
            if(block.getEquation() != null) {
                element.setText(extractElementsText(block.getEquation().getElements()));
            }
            break;
        case 31: // table
            element.setType("Table");
            if(block.getTable() != null) {
                element.setRows(convertTableRows(block, allBlocks, client));
            }
            break;
        case 27: // image
            element.setType("Figure");
            if(block.getImage() != null) {
                DocParseResult.Image image = convertImage(block.getImage(), client);
                element.setImage(image);
            }
            break;
        case 32: // table_cell
            element.setType("Text"); // 表格单元格作为文本处理
            // 表格单元格的内容通过children处理
            break;
        default:
            element.setType("Text");
            element.setText("");
        }

        return element;
    }

    /**
     * 从elements数组中提取文本内容
     * 
     * @param elements 文本元素数组
     * 
     * @return 提取的文本
     */
    private static String extractElementsText(TextElement[] elements) {
        if(elements == null || elements.length == 0) {
            return "";
        }

        StringBuilder text = new StringBuilder();

        for (TextElement element : elements) {
            if(element != null) {
                String content = extractTextFromElement(element);
                if(!content.isEmpty()) {
                    text.append(content);
                }
            }
        }

        return text.toString();
    }

    /**
     * 从单个TextElement中提取文本内容
     * 
     * @param element TextElement对象
     * 
     * @return 提取的文本内容
     */
    private static String extractTextFromElement(TextElement element) {
        if(element == null) {
            return "";
        }

        // 根据TextElement的不同类型提取文本
        if(element.getTextRun() != null && element.getTextRun().getContent() != null) {
            // 普通文本
            return element.getTextRun().getContent();
        } else if(element.getMentionUser() != null) {
            // @用户
            return "@" + (element.getMentionUser().getUserId() != null ? element.getMentionUser().getUserId() : "user");
        } else if(element.getMentionDoc() != null) {
            // @文档
            return "@doc:" + element.getMentionDoc().getTitle();
        } else if(element.getReminder() != null) {
            // 日期提醒
            return "[提醒]" + element.getReminder().getNotifyTime();
        } else if(element.getFile() != null) {
            // 内联附件
            return "[文件] " + element.getMentionDoc().getTitle();
        } else if(element.getEquation() != null) {
            // 公式
            return element.getEquation().getContent();
        }
        return "";
    }

    /**
     * 转换表格行数据
     * 
     * @param block     表格Block
     * @param allBlocks 所有Block列表
     * @param client    LarkClient
     * 
     * @return 行数据列表
     */
    private static List<DocParseResult.Row> convertTableRows(Block block, List<Block> allBlocks, Client client) {
        List<DocParseResult.Row> rows = new ArrayList<>();

        if(block.getTable() == null || block.getTable().getCells() == null ||
                block.getTable().getProperty() == null) {
            return rows;
        }

        try {
            // 获取表格属性
            Integer columnSize = block.getTable().getProperty().getColumnSize();
            Integer rowSize = block.getTable().getProperty().getRowSize();
            String[] cellIds = block.getTable().getCells();

            if(columnSize == null || rowSize == null || cellIds == null) {
                return rows;
            }

            int[][] position = new int[rowSize][columnSize];

            // 构建cellId到Block的映射
            Map<String, Block> cellBlockMap = allBlocks.stream()
                    .filter(b -> b.getTableCell() != null)
                    .collect(Collectors.toMap(
                            Block::getBlockId,
                            java.util.function.Function.identity(),
                            (existing, replacement) -> existing));

            // 按行构建表格
            for (int row = 0; row < rowSize; row++) {
                DocParseResult.Row rowData = new DocParseResult.Row();
                List<DocParseResult.Cell> cells = new ArrayList<>();
                boolean hasValidCells = false;

                // 按列构建单元格
                for (int col = 0; col < columnSize; col++) {

                    int cellIndex = row * columnSize + col;
                    if(cellIndex < cellIds.length) {
                        String cellId = cellIds[cellIndex];
                        Block cellBlock = cellBlockMap.get(cellId);

                        DocParseResult.Cell cell = new DocParseResult.Cell();

                        // 计算单元格的合并信息
                        int rowSpan = 1;
                        int colSpan = 1;

                        // 从表格的merge_info中获取合并信息
                        if(block.getTable().getProperty().getMergeInfo() != null &&
                                cellIndex < block.getTable().getProperty().getMergeInfo().length) {
                            TableMergeInfo mergeInfo = block.getTable().getProperty().getMergeInfo()[cellIndex];
                            if(mergeInfo != null) {
                                rowSpan = mergeInfo.getRowSpan();
                                colSpan = mergeInfo.getColSpan();
                            }
                        }

                        // 记录单元格坐标信息
                        // 计算单元格坐标范围（从1开始）
                        int startRow = row + 1;
                        int endRow = row + rowSpan;
                        int startCol = col + 1;
                        int endCol = col + colSpan;

                        List<Integer> cellCoords = Arrays.asList(startRow, endRow, startCol, endCol);
                        cell.setPath(cellCoords);

                        // 处理复杂单元格：如果不是纯文本，解析为node
                        if(cellBlock != null && cellBlock.getChildren() != null) {
                            List<Block> childBlocks = new ArrayList<>();
                            boolean hasComplexContent = false;

                            // 收集所有子块并检查是否包含复杂内容
                            for (String childId : cellBlock.getChildren()) {
                                Block childBlock = allBlocks.stream()
                                        .filter(b -> childId.equals(b.getBlockId()))
                                        .findFirst()
                                        .orElse(null);

                                if(childBlock != null) {
                                    childBlocks.add(childBlock);
                                    // 检查是否为复杂内容（非纯文本）
                                    if(isComplexBlock(childBlock)) {
                                        hasComplexContent = true;
                                    }
                                }
                            }

                            if(hasComplexContent) {
                                // 包含复杂内容，转换为节点结构
                                List<DocParseResult> cellNodes = buildHierarchicalStructure(childBlocks, allBlocks, client);
                                cell.setNodes(cellNodes);
                                cell.setText(""); // 复杂单元格不设置文本
                            } else {
                                // 纯文本内容，提取文本
                                StringBuilder cellContent = new StringBuilder();
                                for (Block childBlock : childBlocks) {
                                    if(childBlock.getText() != null) {
                                        String childText = extractElementsText(childBlock.getText().getElements());
                                        if(!childText.isEmpty()) {
                                            if(cellContent.length() > 0) {
                                                cellContent.append("\n");
                                            }
                                            cellContent.append(childText);
                                        }
                                    }
                                }
                                cell.setText(cellContent.toString());
                            }
                        } else {
                            cell.setText("");
                        }

                        // 如果单元格有内容，标记该行有有效单元格
                        if(StringUtils.isNotBlank(cell.getText()) || (cell.getNodes() != null && !cell.getNodes().isEmpty())) {
                            hasValidCells = true;
                        } else if(position[row][col] == 1) {
                            continue; // 如果当前单元格内容为空，且已经被占用则跳过
                        }

                        cells.add(cell);

                        // 标记当前单元格占用的所有位置
                        for (int r = row; r < Math.min(endRow, rowSize); r++) {
                            for (int c = col; c < Math.min(endCol, columnSize); c++) {
                                position[r][c] = 1;
                            }
                        }
                    }
                }

                if(hasValidCells) {
                    rowData.setCells(cells);
                    rows.add(rowData);
                }
            }
        } catch (Exception e) {
            // 如果转换失败，返回空列表
            log.warn("转换表格时出错: " + e.getMessage(), e);
        }

        return rows;
    }

    /**
     * 转换图片信息
     * 
     * @param imageBlock 图片Block对象
     * @param client     LarkClient
     * 
     * @return Image对象
     */
    private static DocParseResult.Image convertImage(Image imageBlock, Client client) {
        DocParseResult.Image image = new DocParseResult.Image();
        image.setType("image_base64");
        image.setBase64(getImageUrl(imageBlock, client));

        return image;
    }

}
