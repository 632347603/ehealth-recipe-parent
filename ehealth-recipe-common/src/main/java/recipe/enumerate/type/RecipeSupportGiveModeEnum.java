package recipe.enumerate.type;

import com.ngari.recipe.dto.GiveModeButtonDTO;
import com.ngari.recipe.entity.DrugsEnterprise;
import com.ngari.recipe.entity.OrganAndDrugsepRelation;
import ctd.persistence.exception.DAOException;
import org.apache.commons.collections.CollectionUtils;
import org.springframework.util.StringUtils;
import recipe.constant.RecipeBussConstant;
import recipe.enumerate.status.GiveModeEnum;
import recipe.util.ValidateUtil;

import java.util.*;
import java.util.stream.Collectors;

import static java.util.stream.Collectors.toList;

/**
 * @description： 处方支持的购药方式
 * @author： whf
 * @date： 2021-04-26 13:51
 */
public enum RecipeSupportGiveModeEnum {
    /**
     * 到店取药
     */
    SUPPORT_TFDS("supportTFDS", 1, "到店取药"),

    /**
     * showSendToHos 医院配送
     */
    SHOW_SEND_TO_HOS("showSendToHos", 2, "医院配送"),

    /**
     * showSendToEnterprises 药企配送
     */
    SHOW_SEND_TO_ENTERPRISES("showSendToEnterprises", 3, "药企配送"),

    /**
     * supportToHos 到院取药
     */
    SUPPORT_TO_HOS("supportToHos", 4, "到院取药"),

    /**
     * downloadRecipe 下载处方笺
     */
    DOWNLOAD_RECIPE("supportDownload", 5, "下载处方笺"),


    /**
     * supportMedicalPayment 例外支付
     */
    SUPPORT_MEDICAL_PAYMENT("supportMedicalPayment", 6, "例外支付");


    /**
     * 配送文案
     */
    private String text;
    /**
     * 配送状态
     */
    private Integer type;

    private String name;


    RecipeSupportGiveModeEnum(String text, Integer type, String name) {
        this.text = text;
        this.type = type;
        this.name = name;
    }


    public String getText() {
        return text;
    }

    public Integer getType() {
        return type;
    }

    public String getName() {
        return name;
    }

    /**
     * 获取  GiveModeEnum type
     *
     * @param text
     * @return
     */
    public static Integer getGiveMode(String text) {
        Integer giveModeType = 0;
        switch (getRecipeSupportGiveModeEnum(text)) {
            case SUPPORT_TFDS:
                giveModeType = GiveModeEnum.GIVE_MODE_PHARMACY_DRUG.getType();
                break;
            case SUPPORT_TO_HOS:
                giveModeType = GiveModeEnum.GIVE_MODE_HOSPITAL_DRUG.getType();
                break;
            case DOWNLOAD_RECIPE:
                giveModeType = GiveModeEnum.GIVE_MODE_DOWNLOAD_RECIPE.getType();
                break;
            case SHOW_SEND_TO_HOS:
            case SHOW_SEND_TO_ENTERPRISES:
                giveModeType = GiveModeEnum.GIVE_MODE_HOME_DELIVERY.getType();
                break;
            case SUPPORT_MEDICAL_PAYMENT:
            default:
                break;
        }
        return giveModeType;
    }

    /**
     * 机构与药企关联关系中的购药方式
     * 医院配送与药企配送只能2选1 到院自取与到店自取只能2选1
     *
     * @param giveModeTypes
     */
    public static void checkOrganEnterpriseRelationGiveModeType(List<Integer> giveModeTypes) {
        if (CollectionUtils.isEmpty(giveModeTypes)) {
            return;
        }
        if (giveModeTypes.contains(RecipeSupportGiveModeEnum.SUPPORT_TFDS.type) && giveModeTypes.contains(RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.type)) {
            throw new DAOException("到院自取与到店自取只能2选1");
        }
        if (giveModeTypes.contains(RecipeSupportGiveModeEnum.SHOW_SEND_TO_HOS.type) && giveModeTypes.contains(RecipeSupportGiveModeEnum.SHOW_SEND_TO_ENTERPRISES.type)) {
            throw new DAOException("医院配送与药企配送只能2选1");
        }
    }


    public static RecipeSupportGiveModeEnum getRecipeSupportGiveModeEnum(String text) {
        for (RecipeSupportGiveModeEnum e : RecipeSupportGiveModeEnum.values()) {
            if (e.text.equals(text)) {
                return e;
            }
        }
        return null;
    }

    /**
     * 根据 text获取 int类型
     *
     * @param text key
     * @return int类型
     */
    public static Integer getGiveModeType(String text) {
        for (RecipeSupportGiveModeEnum e : RecipeSupportGiveModeEnum.values()) {
            if (e.text.equals(text)) {
                return e.type;
            }
        }
        return 0;
    }

    /**
     *
     * @param textList
     * @return
     */
    public static List<Integer> getGiveModeTypeList(List<String> textList){
        if (CollectionUtils.isEmpty(textList)) {
            return new ArrayList<>();
        }
        List<Integer> giveModeTypeList = new ArrayList<>();
        textList.forEach(text ->{
            giveModeTypeList.add(getGiveModeType(text));
        });
        return giveModeTypeList;
    }

    public static List<String> enterpriseList = Arrays.asList(SHOW_SEND_TO_HOS.text, SHOW_SEND_TO_ENTERPRISES.text, SUPPORT_TFDS.text,SUPPORT_MEDICAL_PAYMENT.text);

    /**
     * 校验何种类型库存
     *
     * @param configurations 按钮
     * @return
     */
    public static Integer checkFlag(List<String> configurations) {
        int hospital = DrugStockCheckEnum.NO_CHECK_STOCK.getType();
        int enterprise = DrugStockCheckEnum.NO_CHECK_STOCK.getType();
        for (String a : configurations) {
            if (SUPPORT_TO_HOS.getText().equals(a)) {
                hospital = DrugStockCheckEnum.HOS_CHECK_STOCK.getType();
            }
            if (enterpriseList.contains(a)) {
                enterprise = DrugStockCheckEnum.ENT_CHECK_STOCK.getType();
            }
        }
        return hospital + enterprise;
    }

    /**
     * 判断是否校验药企库存
     *
     * @param giveModeButtonBeans
     * @return null 无配置 不校验
     */
    public static List<String> checkEnterprise(List<GiveModeButtonDTO> giveModeButtonBeans) {
        if (CollectionUtils.isEmpty(giveModeButtonBeans)) {
            return null;
        }
        List<String> configurations = giveModeButtonBeans.stream().map(GiveModeButtonDTO::getShowButtonKey).collect(Collectors.toList());
        boolean enterprise = configurations.stream().anyMatch(a -> enterpriseList.contains(a));
        if (!enterprise) {
            return null;
        }
        return configurations;
    }

    /**
     * 根据配送模式与配送主体 确定药企 购药方式
     * 0->gg
     * 1->(2/3)
     * 2->(2/3)
     * 3->1
     * 4->5
     * 7->1,(2/3)
     * 8->1,(2/3)
     * 9->1,(2/3)
     *
     * @param payModeSupport 配送模式支持 0:不支持 1:线上付款 2:货到付款 3:药店取药 4:例外支付 8:货到付款和药店取药 9:都支持
     * @param sendType       配送主体类型 1医院配送 2 药企配送
     * @return 购药方式枚举
     */
    public static List<GiveModeButtonDTO> enterpriseEnum(Integer payModeSupport, Integer sendType) {
        if (ValidateUtil.integerIsEmpty(payModeSupport)) {
            return null;
        }
        List<GiveModeButtonDTO> giveModeButtonList = new LinkedList<>();
        if (RecipeBussConstant.DEP_SUPPORT_TFDS.equals(payModeSupport)) {
            giveModeButtonList.add(giveModeButtonDTO(SUPPORT_TFDS));
            return giveModeButtonList;
        }
        if(RecipeBussConstant.SUPPORT_MEDICAL_PAYMENT.equals(payModeSupport)){
            giveModeButtonList.add(giveModeButtonDTO(SUPPORT_MEDICAL_PAYMENT));
            return giveModeButtonList;
        }
        if (RecipeDistributionFlagEnum.drugsEnterpriseAll.contains(payModeSupport)) {
            giveModeButtonList.add(giveModeButtonDTO(SUPPORT_TFDS));
            giveModeButtonList.add(giveModeButtonDTO(SUPPORT_MEDICAL_PAYMENT));
        }
        //配送判断
        if (ValidateUtil.integerIsEmpty(sendType) || RecipeSendTypeEnum.NO_PAY.getSendType().equals(sendType)) {
            giveModeButtonList.add(giveModeButtonDTO(SHOW_SEND_TO_ENTERPRISES));
        } else {
            giveModeButtonList.add(giveModeButtonDTO(SHOW_SEND_TO_HOS));
        }
        return giveModeButtonList;
    }

    public static GiveModeButtonDTO giveModeButtonDTO(RecipeSupportGiveModeEnum recipeSupportGiveModeEnum) {
        GiveModeButtonDTO giveModeButtonDTO = new GiveModeButtonDTO();
        giveModeButtonDTO.setShowButtonKey(recipeSupportGiveModeEnum.getText());
        giveModeButtonDTO.setType(recipeSupportGiveModeEnum.getType());
        return giveModeButtonDTO;
    }

    /**
     * 根据 药企-机构配置获取 购药按钮对象
     *
     * @param drugsEnterprise   药企信息
     * @param configGiveMode    机构按钮配置
     * @param configGiveModeMap 机构按钮配置 key ：text ， value ： name
     * @return 药企展示的购药按钮
     */
    public static List<GiveModeButtonDTO> giveModeButtonList(DrugsEnterprise drugsEnterprise, List<String> configGiveMode, Map<String, String> configGiveModeMap, Boolean drugToHosByEnterprise, Map<Integer, List<OrganAndDrugsepRelation>> relationMap) {
        List<GiveModeButtonDTO> enterpriseGiveMode = enterpriseEnum(drugsEnterprise.getPayModeSupport(), drugsEnterprise.getSendType());
        if (null == enterpriseGiveMode || null == configGiveMode) {
            return null;
        }
        List<GiveModeButtonDTO> giveModeKey = enterpriseGiveMode.stream().filter(a -> configGiveMode.contains(a.getShowButtonKey())).collect(toList());
        if (CollectionUtils.isEmpty(giveModeKey)) {
            return null;
        }
        giveModeKey.forEach(a -> a.setShowButtonName(configGiveModeMap.get(a.getShowButtonKey())));
        // 采用新模式且机构支持到院取药 且机构药企支持到院取药
        if (drugToHosByEnterprise && !StringUtils.isEmpty(configGiveModeMap.get(RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.text))
                && Objects.nonNull(relationMap) && Objects.nonNull(relationMap.get(drugsEnterprise.getId()))) {
            GiveModeButtonDTO giveModeButtonDTO = new GiveModeButtonDTO();
            giveModeButtonDTO.setShowButtonKey(RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.text);
            giveModeButtonDTO.setShowButtonName(configGiveModeMap.get(RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.text));
            giveModeButtonDTO.setType(RecipeSupportGiveModeEnum.SUPPORT_TO_HOS.getType());
            giveModeKey.add(giveModeButtonDTO);
        }
        return giveModeKey;
    }

    /**
     * 获取 机构配置 购药按钮文案
     *
     * @param giveModeButtonBeans 机构配置按钮
     * @param giveModeButtonBeans 机构配置按钮key
     * @return 按钮文案-》name
     */
    public static String getGiveModeName(List<GiveModeButtonDTO> giveModeButtonBeans, String key) {
        if (CollectionUtils.isEmpty(giveModeButtonBeans)) {
            return null;
        }
        Map<String, String> configurations = giveModeButtonBeans.stream().collect(Collectors.toMap(GiveModeButtonDTO::getShowButtonKey, GiveModeButtonDTO::getShowButtonName));
        String showButtonName = configurations.get(key);
        //无机构配置按钮key
        if (StringUtils.isEmpty(showButtonName)) {
            return null;
        }
        return showButtonName;
    }

    public static GiveModeButtonDTO getGiveMode(List<GiveModeButtonDTO> giveModeButtonBeans, String key) {
        if (CollectionUtils.isEmpty(giveModeButtonBeans)) {
            return null;
        }
        Map<String, GiveModeButtonDTO> configurations = giveModeButtonBeans.stream().collect(Collectors.toMap(GiveModeButtonDTO::getShowButtonKey, a -> a, (k1, k2) -> k1));
        GiveModeButtonDTO showButton = configurations.get(key);
        //无机构配置按钮key
        if (null == showButton) {
            return null;
        }
        return showButton;
    }

}