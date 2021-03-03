package recipe.service.manager;

import com.ngari.base.esign.model.ESignDTO;
import com.ngari.base.esign.service.IESignBaseService;
import com.ngari.base.property.service.IConfigurationCenterUtilsService;
import com.ngari.base.scratchable.service.IScratchableService;
import com.ngari.patient.dto.PatientDTO;
import com.ngari.recipe.drugsenterprise.model.RecipeLabelVO;
import com.ngari.recipe.entity.RecipeExtend;
import com.ngari.recipe.recipe.model.RecipeBean;
import com.ngari.recipe.recipe.model.RecipeDetailBean;
import ctd.dictionary.DictionaryController;
import ctd.persistence.exception.DAOException;
import eh.entity.base.Scratchable;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import recipe.bussutil.RecipeUtil;
import recipe.bussutil.openapi.util.JSONUtils;
import recipe.comment.DictionaryUtil;
import recipe.constant.ErrorCode;
import recipe.util.ByteUtils;
import recipe.util.MapValueUtil;

import javax.annotation.Resource;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * 处方签
 *
 * @author fuzi
 */
@Service
public class RecipeLabelManager {
    private final Logger logger = LoggerFactory.getLogger(this.getClass());

    /**
     * 运营平台机构配置，处方签配置， 特殊字段替换展示
     */
    private final static List<String> CONFIG_STRING = Arrays.asList("recipeDetailRemark");

    @Autowired
    private IScratchableService scratchableService;

    @Autowired
    private IConfigurationCenterUtilsService configService;
    @Resource
    private IESignBaseService esignService;

    /**
     * 因为pdf动态配置无法得知再次写入时所需高度，故计算
     * 根据运营平台配置的模块二数量计算 收获人/收获地址的写入高度
     *
     * @param organId 机构id
     * @return Y坐标点位 （默认460）
     */
    public int getPdfReceiverHeight(Integer organId) {
        int height = 460;
        if (null == organId) {
            return height;
        }
        Map<String, Object> labelMap = scratchableService.findRecipeListDetail(organId.toString());
        if (CollectionUtils.isEmpty(labelMap)) {
            return height;
        }
        List<Scratchable> moduleOne = (List<Scratchable>) labelMap.get("moduleOne");
        List<Scratchable> moduleTwo = (List<Scratchable>) labelMap.get("moduleTwo");
        if (CollectionUtils.isEmpty(moduleOne) && CollectionUtils.isEmpty(moduleTwo)) {
            return height;
        }
        if (!CollectionUtils.isEmpty(moduleOne) && !CollectionUtils.isEmpty(moduleTwo)) {
            height = 429;
        }
        if (!CollectionUtils.isEmpty(moduleOne) && CollectionUtils.isEmpty(moduleTwo)) {
            height = 454;
        }
        if (CollectionUtils.isEmpty(moduleOne) && !CollectionUtils.isEmpty(moduleTwo)) {
            height = 442;
        }
        height = getHeight(moduleOne, 3, height);
        height = getHeight(moduleTwo, 6, height);
        return height;
    }

    /**
     * 根据 非标准配置值参数个数 与 标准参数配置计算浮动高度
     *
     * @param module     非标准配置值参数个数
     * @param initialize 标准参数配置计算浮动高度
     * @param height     高度
     * @return
     */
    private int getHeight(List<Scratchable> module, int initialize, int height) {
        if (CollectionUtils.isEmpty(module)) {
            return height;
        }
        int size = module.size();
        if (size <= initialize) {
            return height;
        }
        int formatting = (size - initialize) / 3;
        if (0 != (size - initialize) % 3) {
            formatting++;
        }
        height = height - formatting * 12;
        return height;
    }

    /**
     * 获取pdf oss id
     *
     * @param result
     * @param recipeMap
     * @return
     */
    public Map<String, Object> queryPdfRecipeLabelById(Map<String, List<RecipeLabelVO>> result, Map<String, Object> recipeMap) {
        PatientDTO patientDTO = (PatientDTO) recipeMap.get("patient");
        RecipeBean recipe = (RecipeBean) recipeMap.get("recipe");
        Integer recipeId = recipe.getRecipeId();
        //组装生成pdf的参数
        ESignDTO eSignDTO = new ESignDTO();
        String recipeType = DictionaryUtil.getDictionary("eh.cdr.dictionary.RecipeType", recipe.getRecipeType());
        eSignDTO.setRecipeType(recipeType);
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            //中药pdf参数
            eSignDTO.setTemplateType("tcm");
            createChineMedicinePDF(result, recipeMap, recipe);
        } else {
            eSignDTO.setTemplateType("wm");
            createMedicinePDF(result, recipe);
            eSignDTO.setImgFileId(recipeId.toString());
        }
        eSignDTO.setLoginId(patientDTO.getLoginId());
        eSignDTO.setDoctorName(recipe.getDoctorName());
        eSignDTO.setDoctorId(recipe.getDoctor());
        eSignDTO.setOrgan(recipe.getClinicOrgan());
        eSignDTO.setFileName("recipe_" + recipeId + ".pdf");
        eSignDTO.setParamMap(Collections.unmodifiableMap(result));
        Object rpTorx = configService.getConfiguration(recipe.getClinicOrgan(), "rptorx");
        eSignDTO.setRp(String.valueOf(rpTorx));
        Map<String, Object> backMap = esignService.signForRecipe2(eSignDTO);
        logger.info("RecipeLabelManager queryPdfRecipeLabelById backMap={},eSignDTO={}", JSONUtils.toString(backMap), JSONUtils.toString(eSignDTO));
        return backMap;
    }

    /**
     * 获取pdf byte 格式
     *
     * @param result
     * @param recipeMap
     * @return
     */
    public String queryPdfStrById(Map<String, List<RecipeLabelVO>> result, Map<String, Object> recipeMap) {
        RecipeBean recipe = (RecipeBean) recipeMap.get("recipe");
        //组装生成pdf的参数
        Map<String, Object> map = new HashMap<>();
        String recipeType = DictionaryUtil.getDictionary("eh.cdr.dictionary.RecipeType", recipe.getRecipeType());
        map.put("recipeType", recipeType);
        if (RecipeUtil.isTcmType(recipe.getRecipeType())) {
            //中药pdf参数
            map.put("templateType", "tcm");
            createChineMedicinePDF(result, recipeMap, recipe);
        } else {
            map.put("templateType", "wm");
            createMedicinePDF(result, recipe);
        }
        Object rpTorx = configService.getConfiguration(recipe.getClinicOrgan(), "rptorx");
        map.put("rp", String.valueOf(rpTorx));
        map.put("paramMap", result);
        logger.info("RecipeLabelManager queryPdfRecipeLabelById map={}", JSONUtils.toString(map));
        return esignService.createSignRecipePDF(map);
    }

    /**
     * 获取处方签 配置 给前端展示。
     * 1获取处方信息，2获取运营平台配置，3替换运营平台配置字段值，4返回对象给前端展示
     *
     * @param recipeMap 处方
     * @param organId   机构id
     * @return
     */
    public Map<String, List<RecipeLabelVO>> queryRecipeLabelById(Integer organId, Map<String, Object> recipeMap) {
        logger.info("RecipeLabelManager queryRecipeLabelById ,organId={}", organId);
        if (null == organId) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "机构id为空");
        }
        Map<String, Object> labelMap = scratchableService.findRecipeListDetail(organId.toString());
        if (CollectionUtils.isEmpty(labelMap)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "运营平台配置为空");
        }
        //处理特殊字段
        setRecipeMap(recipeMap, (List<Scratchable>) labelMap.get("moduleFive"));

        Map<String, List<RecipeLabelVO>> resultMap = new HashMap<>();
        labelMap.forEach((k, v) -> {
            List<Scratchable> value = (List<Scratchable>) v;
            if (CollectionUtils.isEmpty(value)) {
                logger.warn("RecipeLabelManager queryRecipeLabelById value is null k={} ", k);
                return;
            }
            try {
                List<RecipeLabelVO> list = getValue(value, recipeMap, organId);
                resultMap.put(k, list);
            } catch (Exception e) {
                logger.error("RecipeLabelManager queryRecipeLabelById error ", e);
            }
        });
        logger.info("RecipeLabelManager queryRecipeLabelById resultMap={}", JSONUtils.toString(resultMap));
        return resultMap;
    }

    /**
     * 根据运营平台配置 组织字段值对象
     * todo 暂时需求如此 仅支持固定格式解析 （patient.patientName 这种一层对象解析）
     *
     * @param scratchableList
     * @param recipeMap
     * @return
     */
    private List<RecipeLabelVO> getValue(List<Scratchable> scratchableList, Map<String, Object> recipeMap, Integer organId) {
        logger.info("RecipeLabelManager getValue scratchableList ={} recipeMap={}", JSONUtils.toString(scratchableList), JSONUtils.toString(recipeMap));
        List<RecipeLabelVO> recipeLabelList = new LinkedList<>();
        scratchableList.forEach(a -> {
            if (StringUtils.isEmpty(a.getBoxLink())) {
                return;
            }

            String boxLink = a.getBoxLink().trim();
            /**根据模版匹配 value*/
            Object value = recipeMap.get(boxLink);
            if (CONFIG_STRING.contains(boxLink)) {
                value = configService.getConfiguration(organId, boxLink);
                if (null == value) {
                    return;
                }
            }

            if (null == value) {
                //对象获取字段
                String[] boxLinks = boxLink.split(ByteUtils.DOT);
                Object key = recipeMap.get(boxLinks[0]);
                if (2 == boxLinks.length && null != key) {
                    if (key instanceof List && !CollectionUtils.isEmpty((List) key)) {
                        key = ((List) key).get(0);
                    }
                    value = MapValueUtil.getFieldValueByName(boxLinks[1], key);
                } else {
                    logger.warn("RecipeLabelManager getValue boxLinks ={}", JSONUtils.toString(boxLinks));
                }
            }

            //组织返回对象
            recipeLabelList.add(new RecipeLabelVO(a.getBoxTxt(), boxLink, value));
        });
        return recipeLabelList;
    }

    /**
     * 处理特殊模版匹配规则
     *
     * @param recipeMap
     */
    private void setRecipeMap(Map<String, Object> recipeMap, List<Scratchable> list) {
        if (CollectionUtils.isEmpty(recipeMap)) {
            throw new DAOException(ErrorCode.SERVICE_ERROR, "recipeMap is null!");
        }
        //处理性别转化
        PatientDTO patientDTO = (PatientDTO) recipeMap.get("patient");
        if (null != patientDTO && StringUtils.isNotEmpty(patientDTO.getPatientSex())) {
            patientDTO.setPatientSex(DictionaryUtil.getDictionary("eh.base.dictionary.Gender", String.valueOf(patientDTO.getPatientSex())));
        }
        //签名字段替换
        String doctorSignImg = null == recipeMap.get("doctorSignImg") ? "" : recipeMap.get("doctorSignImg").toString();
        String doctorSignImgToken = null == recipeMap.get("doctorSignImgToken") ? "" : recipeMap.get("doctorSignImgToken").toString();
        if (!StringUtils.isAnyEmpty(doctorSignImg, doctorSignImgToken)) {
            recipeMap.put("doctorSignImg,doctorSignImgToken", doctorSignImg + ByteUtils.COMMA + doctorSignImgToken);
        }
        String checkerSignImg = null == recipeMap.get("checkerSignImg") ? "" : recipeMap.get("checkerSignImg").toString();
        String checkerSignImgToken = null == recipeMap.get("checkerSignImgToken") ? "" : recipeMap.get("checkerSignImgToken").toString();

        RecipeBean recipeBean = (RecipeBean) recipeMap.get("recipe");
        if (!StringUtils.isAnyEmpty(checkerSignImg, checkerSignImgToken)) {
            recipeMap.put("checkerSignImg,checkerSignImgToken", checkerSignImg + ByteUtils.COMMA + checkerSignImgToken);
        } else if (null != recipeBean && StringUtils.isNotEmpty(recipeBean.getCheckerText())) {
            recipeMap.put("checkerSignImg,checkerSignImgToken", recipeBean.getCheckerText());
        }
        //机构名称替换
        if (!CollectionUtils.isEmpty(list)) {
            String boxDesc = null;
            for (Scratchable scratchable : list) {
                if ("recipe.organName".equals(scratchable.getBoxLink()) && StringUtils.isNotEmpty(scratchable.getBoxDesc())) {
                    boxDesc = scratchable.getBoxDesc();
                }
            }
            if (StringUtils.isNotEmpty(boxDesc)) {
                if (null != recipeBean) {
                    recipeBean.setOrganName(boxDesc);
                }
            }
        }
    }

    /**
     * 西药 pdf 摸版参数
     *
     * @param result
     * @param recipe
     */
    private void createMedicinePDF(Map<String, List<RecipeLabelVO>> result, RecipeBean recipe) {
        List<RecipeLabelVO> list = result.get("moduleThree");
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<RecipeDetailBean> recipeDetailList = (List<RecipeDetailBean>) list.get(0).getValue();
        for (int i = 0; i < recipeDetailList.size(); i++) {
            RecipeDetailBean d = recipeDetailList.get(i);
            //名称+规格+药品单位+开药总量+药品单位
            StringBuilder stringBuilder = new StringBuilder(i + 1);
            stringBuilder.append(i + 1).append("、").append(d.getDrugName()).append(d.getDrugSpec()).append("/").append(d.getDrugUnit())
                    .append("   ").append("X").append(d.getUseTotalDose()).append(d.getDrugUnit());
            Object canShowDrugCost = configService.getConfiguration(recipe.getClinicOrgan(), "canShowDrugCost");
            if ((boolean) canShowDrugCost) {
                BigDecimal drugCost = d.getDrugCost().divide(BigDecimal.ONE, 2, RoundingMode.UP);
                stringBuilder.append("   ").append(drugCost).append("元");
            }
            stringBuilder.append(" \n ");
            //每次剂量+剂量单位
            String useDose = null == d.getUseDose() ? "" : d.getUseDose() + d.getUseDoseUnit();
            String uDose = "Sig: 每次" + useDose;

            //用药频次
            String dRateName = d.getUsingRateTextFromHis() != null ? d.getUsingRateTextFromHis() : DictionaryUtil.getDictionary("eh.cdr.dictionary.UsingRate", d.getUsingRate());
            //用法
            String dWay = d.getUsePathwaysTextFromHis() != null ? d.getUsePathwaysTextFromHis() : DictionaryUtil.getDictionary("eh.cdr.dictionary.UsePathways", d.getUsePathways());
            String useDay = d.getUseDays() + "天";
            stringBuilder.append(uDose).append("    ").append(dRateName).append("    ").append(dWay).append("    ").append(useDay);

            if (!StringUtils.isEmpty(d.getMemo())) {
                stringBuilder.append(" \n ").append("备注:").append(d.getMemo());
            }
            list.add(new RecipeLabelVO("medicine", "drugInfo" + i, stringBuilder.toString()));
        }
    }

    /**
     * 中药pdf 摸版参数
     *
     * @param result
     * @param recipeMap
     * @param recipe
     */
    private void createChineMedicinePDF(Map<String, List<RecipeLabelVO>> result, Map<String, Object> recipeMap, RecipeBean recipe) {
        List<RecipeLabelVO> list = result.get("moduleThree");
        if (CollectionUtils.isEmpty(list)) {
            return;
        }
        List<RecipeDetailBean> recipeDetailList = (List<RecipeDetailBean>) list.get(0).getValue();
        for (int i = 0; i < recipeDetailList.size(); i++) {
            RecipeDetailBean detail = recipeDetailList.get(i);
            String dTotal;
            if (StringUtils.isNotEmpty(detail.getUseDoseStr())) {
                dTotal = detail.getUseDoseStr() + detail.getUseDoseUnit();
            } else {
                dTotal = detail.getUseDose() + detail.getUseDoseUnit();
            }
            if (!StringUtils.isEmpty(detail.getMemo())) {
                dTotal = dTotal + "*" + detail.getMemo();
            }
            list.add(new RecipeLabelVO("chineMedicine", "drugInfo" + i, detail.getSaleName() + ":" + dTotal));
        }
        RecipeDetailBean detail = recipeDetailList.get(0);
        list.add(new RecipeLabelVO("天数", "tcmUseDay", (StringUtils.isEmpty(detail.getUseDaysB()) ? detail.getUseDays() : detail.getUseDaysB()) + "天"));
        try{
            list.add(new RecipeLabelVO("用药途径", "tcmUsePathways", DictionaryController.instance().get("eh.cdr.dictionary.UsePathways").getText(detail.getUsePathways())));
            list.add(new RecipeLabelVO("用药频次", "tcmUsingRate", DictionaryController.instance().get("eh.cdr.dictionary.UsingRate").getText(detail.getUsingRate())));
        }catch (Exception e){
            logger.error("用药途径 用药频率有误");
        }
        list.add(new RecipeLabelVO("贴数", "copyNum", recipe.getCopyNum() + "贴"));
        RecipeExtend extend = (RecipeExtend) recipeMap.get("recipeExtend");
        if (null != extend) {
            list.add(new RecipeLabelVO("煎法", "tcmDecoction", extend.getDecoctionText()==null?"":extend.getDecoctionText()));
            list.add(new RecipeLabelVO("每付取汁", "tcmJuice", extend.getJuice() + extend.getJuiceUnit()));
            list.add(new RecipeLabelVO("次量", "tcmMinor", extend.getMinor() + extend.getMinorUnit()));
            list.add(new RecipeLabelVO("制法", "tcmMakeMethod", extend.getMakeMethodText()==null?"":extend.getMakeMethodText()));
        }
        list.add(new RecipeLabelVO("嘱托", "tcmRecipeMemo", recipe.getRecipeMemo()==null?"":recipe.getRecipeMemo()));
    }


}
