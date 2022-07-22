package recipe.enumerate.status;

import java.util.Arrays;
import java.util.List;

/**
 * 处方状态枚举
 *
 * @author fuzi
 */
public enum RecipeStateEnum {
    /**
     * 处方父状态
     */
    NONE(0, "", ""),
    PROCESS_STATE_SUBMIT(1, "待提交", ""),
    PROCESS_STATE_AUDIT(2, "待审核", ""),
    PROCESS_STATE_ORDER(3, "待下单", ""),
    PROCESS_STATE_DISPENSING(4, "待发药", ""),
    PROCESS_STATE_DISTRIBUTION(5, "配送中", ""),
    PROCESS_STATE_MEDICINE(6, "待取药", ""),
    PROCESS_STATE_DONE(7, "已完成", ""),
    PROCESS_STATE_DELETED(8, "已删除", ""),
    PROCESS_STATE_CANCELLATION(9, "已作废", ""),

    /**
     * 处方子状态:待提交
     */
    SUB_SUBMIT_TEMPORARY(11, "请尽快提交处方", ""),
    SUB_SUBMIT_CHECKING_HOS(12, "您已提交，等待医院HIS确认", ""),
    SUB_SUBMIT_DOC_SIGN_ING(13, "您已提交，等待签名完成", "医生签名中"),
    SUB_SUBMIT_DOC_SIGN_FAIL(14, "签名失败，您可以重新发起签名", "医生签名失败"),

    /**
     * 处方子状态:待审核
     */
    SUB_AUDIT_READY_SUPPORT(21, "等待药师审核", ""),
    SUB_AUDIT_READY_DONE(22, "药师审核完成", ""),
    SUB_AUDIT_DOCTOR_READY(23, "药师审核未通过，医生确认中", ""),

    /**
     * 处方子状态:已完成
     */
    SUB_DONE_DOWNLOAD(71, "下载处方笺", ""),
    SUB_DONE_OD_PAYMENT(72, "门诊缴费下单", ""),
    SUB_DONE_UPLOAD_THIRD(73, "上传到第三方", ""),
    SUB_DONE_SELF_TAKE(74, "自取核销", ""),
    SUB_DONE_SEND(75, "发药签收", ""),

    /**
     * 处方子状态:删除
     */
    SUB_DELETED_REVISIT_END(81, "复诊结束", ""),
    SUB_DELETED_DOCTOR_NOT_SUBMIT(82, "医生未提交删除", ""),

    /**
     * 处方子状态:作废
     */
    SUB_CANCELLATION_DOCTOR(91, "医生撤销", ""),
    SUB_CANCELLATION_AUDIT_NOT_PASS(92, "药师审核未通过", "药师不双签，审核不通过"),
    SUB_CANCELLATION_REFUSE_ORDER(93, "售药方拒绝订单", "已拒发"),
    SUB_CANCELLATION_RETURN_DRUG(94, "售药方退药", "已退药"),
    SUB_CANCELLATION_TIMEOUT_NOT_MEDICINE(95, "患者超时未取药", "患者未取药"),
    SUB_CANCELLATION_TIMEOUT_NOT_ORDER(96, "已过有效期未下单", "过期处方（未支付过期 /未处理过期）"),
    SUB_CANCELLATION_WRITE_HIS_NOT_ORDER(97, "医院his确认失败:", ""),

    ;

    private Integer type;
    private String name;
    private String desc;

    RecipeStateEnum(Integer type, String name, String desc) {
        this.type = type;
        this.name = name;
        this.desc = desc;
    }

    public Integer getType() {
        return type;
    }


    public String getName() {
        return name;
    }

    public static final List<Integer> STATE_DELETED = Arrays.asList(PROCESS_STATE_DELETED.type, PROCESS_STATE_CANCELLATION.type);

    /**
     * 根据类型 获取枚举类型
     *
     * @param type
     * @return
     */
    public static RecipeStateEnum getRecipeStateEnum(Integer type) {
        for (RecipeStateEnum e : RecipeStateEnum.values()) {
            if (e.type.equals(type)) {
                return e;
            }
        }
        return NONE;
    }

}
