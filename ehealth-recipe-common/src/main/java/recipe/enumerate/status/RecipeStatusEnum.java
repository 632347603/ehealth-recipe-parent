package recipe.enumerate.status;

import java.util.Arrays;
import java.util.List;

/**
 * 处方状态枚举
 *
 * @author fuzi
 */
public enum RecipeStatusEnum {
    /**
     * 处方状态
     */
    NONE(-9, "未知", ""),
    RECIPE_STATUS_CHECK_NOT_PASS(-1, "审核未通过", "审核未通过(HIS平台)"),
    RECIPE_STATUS_UNSIGNED(0, "未签名", "未签名"),
    RECIPE_STATUS_UNCHECK(1, "待审核", "待审核"),
    RECIPE_STATUS_CHECK_PASS(2, "待处理", " 审核通过(医院平台)"),
    RECIPE_STATUS_HAVE_PAY(3, "已支付，待取药", "已支付 (HIS回传状态)"),
    RECIPE_STATUS_IN_SEND(4, "配送中", ""),
    RECIPE_STATUS_WAIT_SEND(5, "待配送", ""),
    RECIPE_STATUS_FINISH(6, "已完成", ""),
    RECIPE_STATUS_CHECK_PASS_YS(7, "审核通过", ""),
    RECIPE_STATUS_READY_CHECK_YS(8, "待审核", "待药师审核"),
    RECIPE_STATUS_REVOKE(9, "已撤销", "此处为已撤销的取消状态"),
    RECIPE_STATUS_DELETE(10, "已删除", " 已删除(医生端历史处方不可见)"),
    RECIPE_STATUS_HIS_FAIL(11, "写入his失败", "已取消：HIS写入失败"),

    RECIPE_STATUS_NO_DRUG(12, "已取消", "已取消：患者未取药"),

    RECIPE_STATUS_NO_PAY(13, "未支付", "已取消:超过3天未支付"),
    RECIPE_STATUS_NO_OPERATOR(14, "未处理", "已取消:超过3天未操作"),
    RECIPE_STATUS_CHECK_NOT_PASS_YS(15, "审核不通过", "审核未通过(药师平台人工审核)"),
    RECIPE_STATUS_CHECKING_HOS(16, "医院确认中", "医院审核确认中"),
    RECIPE_STATUS_RECIPE_FAIL(17, "失败", "已取消：取药失败"),
    RECIPE_STATUS_RECIPE_DOWNLOADED(18, "待取药", "已下载：处方已下载"),

    RECIPE_STATUS_RECIPE_MEDICAL_FAIL(19, "已取消", "取消：医保上传失败"),
    RECIPE_STATUS_EXPIRED(20, "天猫已过期", "天猫已过期"),
    RECIPE_STATUS_USING(22, "处理中", "天猫使用中"),
    RECIPE_STATUS_RETURNED(23, "天猫已退回", "天猫已退回"),
    RECIPE_STATUS_CHECKING_MEDICAL_INSURANCE(24, "医保上传确认中", "医保上传确认中"),
    RECIPE_STATUS_NO_MEDICAL_INSURANCE_RETURN(25, "已取消", "取消:医保上传确认中--三天未回传"),
    RECIPE_STATUS_SIGN_ERROR_CODE_DOC(26, "签名失败", "签名失败-医生"),
    RECIPE_STATUS_SIGN_ERROR_CODE_PHA(27, "待审核", "签名失败-药师"),
    RECIPE_STATUS_SIGN_SUCCESS_CODE_DOC(28, "签名成功", "签名成功-医生"),
    RECIPE_STATUS_SIGN_SUCCESS_CODE_PHA(29, "审核成功", "签名成功-药师"),

    RECIPE_STATUS_SIGN_ING_CODE_DOC(30, "处方签名中", "签名中-医生"),
    RECIPE_STATUS_SIGN_ING_CODE_PHA(31, "待审核", "签名中-药师"),
    RECIPE_STATUS_SIGN_NO_CODE_PHA(32, "待审核", "未签名-药师"),

    RECIPE_STATUS_DONE_DISPENSING(40, "已发药", ""),
    RECIPE_STATUS_DECLINE(41, "已拒发", ""),
    RECIPE_STATUS_DRUG_WITHDRAWAL(42, "已退药", ""),
    REVIEW_DRUG_FAIL(43, "已取消", "由于审方平台接口异常，处方单已取消，请稍后重试"),

    ;

    private Integer type;
    private String name;
    private String desc;
    private static List<Integer> recipeStatusCheckList = Arrays.asList(RECIPE_STATUS_SIGN_ING_CODE_PHA.type, RECIPE_STATUS_READY_CHECK_YS.type,RECIPE_STATUS_CHECK_PASS.type);

    RecipeStatusEnum(Integer type, String name, String desc) {
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


    /**
     * 待审核 list
     */
    public static final List<Integer> READY_CHECK = Arrays.asList(RECIPE_STATUS_SIGN_ERROR_CODE_PHA.getType(), RECIPE_STATUS_SIGN_ING_CODE_PHA.getType()
            , RECIPE_STATUS_SIGN_NO_CODE_PHA.getType(), RECIPE_STATUS_READY_CHECK_YS.getType());

    /**
     * 根据类型获取名称
     *
     * @param type
     * @return
     */
    public static String getRecipeStatus(Integer type) {
        for (RecipeStatusEnum e : RecipeStatusEnum.values()) {
            if (e.type.equals(type)) {
                return e.name;
            }
        }
        return NONE.getName() + type;
    }

    /**
     * 根据类型 获取枚举类型
     *
     * @param type
     * @return
     */
    public static RecipeStatusEnum getRecipeStatusEnum(Integer type) {
        for (RecipeStatusEnum e : RecipeStatusEnum.values()) {
            if (e.type.equals(type)) {
                return e;
            }
        }
        return NONE;
    }

    /**
     * 查询处方是否在校验中
     * @param type
     * @return
     */
   public static Boolean getCheckStatusFlag(Integer type){
        if(recipeStatusCheckList.contains(type)){
            return true;
        }else {
            return false;
        }
   }
}
