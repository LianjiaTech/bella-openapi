package com.ke.bella.openapi.protocol.document.parse;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;
import java.util.List;

/**
 * 文档解析结果节点
 */
@Data
public class DocParseResult {
    /**
     * 文档来源
     */
    @JsonProperty("source_file")
    private SourceFile sourceFile;
    
    /**
     * 摘要
     */
    private String summary;
    
    /**
     * token预估数量
     */
    private Integer tokens;
    
    /**
     * 编号的层级信息，例如：[1,2,1]
     */
    private List<Integer> path;
    
    /**
     * 元素信息
     */
    private Element element;
    
    /**
     * 子节点信息
     */
    private List<DocParseResult> children;
    
    /**
     * 元素信息
     */
    @Data
    public static class Element {
        /**
         * 元素类型：Text、Title、List、Catalog、Table、Figure、Formula、Code、ListItem
         */
        private String type;
        
        /**
         * 位置信息，可能跨页所以是个数组
         */
        private List<Position> positions;
        
        /**
         * 如果类型是Table、Figure为其名字
         */
        private String name;
        
        /**
         * 如果类型是Table、Figure为其描述
         */
        private String description;
        
        /**
         * 文本信息，图片ocr的文字
         */
        private String text;
        
        /**
         * 图片信息
         */
        private Image image;
        
        /**
         * 表格才有的属性，表格的行
         */
        private List<Row> rows;
    }
    
    /**
     * 位置信息
     */
    @Data
    public static class Position {
        /**
         * 文档中的矩形坐标信息，例如：[90.1,263.8,101.8,274.3]
         */
        private List<Double> bbox;
        
        /**
         * 页码
         */
        private Integer page;
    }
    
    /**
     * 图片信息
     */
    @Data
    public static class Image {
        /**
         * 图片类型：image_url、image_base64、image_file
         */
        private String type;
        
        /**
         * 链接地址
         */
        private String url;
        
        /**
         * 图片base64编码
         */
        private String base64;
        
        /**
         * 上传到file-api的文件ID
         */
        @JsonProperty("file_id")
        private String fileId;
    }
    
    /**
     * 表格行信息
     */
    @Data
    public static class Row {
        /**
         * 单元格属性
         */
        private List<Cell> cells;
    }
    
    /**
     * 单元格信息
     */
    @Data
    public static class Cell {
        /**
         * 单元格路径
         */
        private Object path;
        
        /**
         * 文本内容
         */
        private String text;
    }
}
