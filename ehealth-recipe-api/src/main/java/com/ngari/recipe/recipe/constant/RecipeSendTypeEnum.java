package com.ngari.recipe.recipe.constant;

import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * 配送主体类型
 */
public enum RecipeSendTypeEnum {

    /**
     * 药企配送
     */
    NO_PAY("药企配送", 1),

    /**
     * 医院配送
     */
    ALRAEDY_PAY("医院配送", 2),
    ;

    /**
     * 配送文案
     */
    private String sendText;
    /**
     * 配送状态
     */
    private Integer sendType;

    private static final Map<Integer, String> map = Arrays.stream(RecipeSendTypeEnum.values())
            .collect(Collectors.toMap(RecipeSendTypeEnum::getSendType, RecipeSendTypeEnum::getSendText));


    RecipeSendTypeEnum(String sendText, Integer sendType) {
        this.sendText = sendText;
        this.sendType = sendType;
    }


    public static String getSendText(Integer sendType) {
        return map.get(sendType);
    }

    public String getSendText() {
        return sendText;
    }

    public Integer getSendType() {
        return sendType;
    }
}