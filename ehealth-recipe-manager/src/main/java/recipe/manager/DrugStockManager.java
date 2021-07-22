package recipe.manager;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONArray;
import com.alibaba.fastjson.JSONObject;
import com.google.common.base.Joiner;
import com.ngari.common.mode.HisResponseTO;
import com.ngari.his.recipe.mode.DrugInfoResponseTO;
import com.ngari.platform.recipe.mode.RecipeResultBean;
import com.ngari.recipe.entity.*;
import ctd.util.JSONUtils;
import org.apache.commons.collections.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import recipe.client.DrugStockClient;
import recipe.client.IConfigurationClient;
import recipe.dao.*;
import recipe.util.ListValueUtil;
import recipe.util.ValidateUtil;

import javax.annotation.Resource;
import java.util.*;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Collectors;


/**
 * @description： 库存检查
 * @author： whf
 * @date： 2021-07-19 9:48
 */
@Service
public class DrugStockManager extends BaseManager {

    @Resource
    private IConfigurationClient configurationClient;

    @Resource
    private DrugStockClient drugStockClient;

    @Resource
    private PharmacyTcmDAO pharmacyTcmDAO;

    @Resource
    private OrganDrugListDAO organDrugListDAO;

    @Autowired
    private OrganAndDrugsepRelationDAO organAndDrugsepRelationDAO;
    @Resource
    private SaleDrugListDAO saleDrugListDAO;
    @Autowired
    private DrugListDAO drugListDAO;


    /**
     * 药企库存
     *
     * @param recipe
     * @param drugsEnterprise
     * @param recipeDetails
     * @return
     */
    public Integer scanEnterpriseDrugStock(Recipe recipe, DrugsEnterprise drugsEnterprise, List<Recipedetail> recipeDetails) {
        List<Integer> drugIds = recipeDetails.stream().map(Recipedetail::getDrugId).collect(Collectors.toList());
        List<SaleDrugList> saleDrugLists = saleDrugListDAO.findByOrganIdAndDrugIds(drugsEnterprise.getId(), drugIds);
        HisResponseTO hisResponseTO = drugStockClient.scanEnterpriseDrugStock(recipe, drugsEnterprise, recipeDetails, saleDrugLists);
        if (hisResponseTO != null && hisResponseTO.isSuccess()) {
            return 1;
        } else {
            return 0;
        }
    }


    /**
     * 检查开处方是否需要进行药企库存校验
     *
     * @param organId
     * @return true:需要校验  false:不需要校验
     */
    public boolean checkEnterprise(Integer organId) {
        Integer checkEnterprise = configurationClient.getCheckEnterpriseByOrganId(organId);
        if (ValidateUtil.integerIsEmpty(checkEnterprise)) {
            return false;
        }
        //获取机构配置的药企是否存在 如果有则需要校验 没有则不需要
        List<DrugsEnterprise> enterprise = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(organId, 1);
        return CollectionUtils.isNotEmpty(enterprise);
    }


    /**
     * 校验医院库存
     *
     * @param recipe
     * @param recipeDetails
     * @return
     */
    public RecipeResultBean scanDrugStockByRecipeId(Recipe recipe,List<Recipedetail> recipeDetails) {
        logger.info("scanHisDrugStockByRecipeId req recipe={}  recipeDetails = {}", JSONArray.toJSONString(recipe),JSONArray.toJSONString(recipeDetails));
        if (Integer.valueOf(1).equals(recipe.getTakeMedicine())) {
            //外带药处方则不进行校验
            return RecipeResultBean.getSuccess();
        }
        return scanDrugStock(recipe, recipeDetails);
    }



    /**
     * 医院库存查询
     *
     * @param recipe
     * @param detailList
     * @return
     */
    public RecipeResultBean scanDrugStock(Recipe recipe, List<Recipedetail> detailList) {
        logger.info("scanDrugStock 入参 recipe={},recipedetail={}", JSONObject.toJSONString(recipe), JSONObject.toJSONString(detailList));
        RecipeResultBean result = RecipeResultBean.getSuccess();

        if (Objects.isNull(recipe)) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("没有该处方");
            return result;
        }

        if (CollectionUtils.isEmpty(detailList)) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("处方没有详情");
            return result;
        }

        // 判断是否需要对接HIS
        List<String> recipeTypes = configurationClient.getValueListCatch(recipe.getClinicOrgan(), "getRecipeTypeToHis", null);
        if (!recipeTypes.contains(Integer.toString(recipe.getRecipeType()))) {
            return result;
        }

        // 判断his 是否存在
        if (!configurationClient.isHisEnable(recipe.getClinicOrgan())) {
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院HIS未启用。");
            logger.info("scanDrugStock 医院HIS未启用 organId: {}", recipe.getClinicOrgan());
            return result;
        }
        Set<Integer> pharmaIds = new HashSet<>();
        AtomicReference<Boolean> organDrugCodeFlag = new AtomicReference<>(false);
        List<Integer> drugIdList = detailList.stream().map(detail -> {
            pharmaIds.add(detail.getPharmacyId());
            if (StringUtils.isEmpty(detail.getOrganDrugCode())) {
                organDrugCodeFlag.set(true);
            }
            return detail.getDrugId();
        }).collect(Collectors.toList());
        // 医院配置药品不能存在机构药品编号为空的情况
        if (organDrugCodeFlag.get()) {
            logger.warn("scanDrugStock 医院配置药品存在编号为空的数据.");
            result.setCode(RecipeResultBean.FAIL);
            result.setError("医院配置药品存在编号为空的数据");
            return result;
        }

        // 请求his
        List<OrganDrugList> organDrugList = organDrugListDAO.findByOrganIdAndDrugIds(recipe.getClinicOrgan(), drugIdList);
        List<PharmacyTcm> pharmacyTcmByIds = pharmacyTcmDAO.getPharmacyTcmByIds(pharmaIds);
        DrugInfoResponseTO response = drugStockClient.scanDrugStock(detailList, recipe.getClinicOrgan(), organDrugList, pharmacyTcmByIds);
        if (null == response) {
            //his未配置该服务则还是可以通过
            result.setError("HIS返回为NULL");
            return result;
        }
        if (0 != response.getMsgCode()) {
            String organCodeStr = response.getMsg();
            List<String> nameList = new ArrayList<>();
            if (StringUtils.isNotEmpty(organCodeStr)) {
                List<String> organCodes = Arrays.asList(organCodeStr.split(","));
                nameList = organDrugListDAO.findNameByOrganIdAndDrugCodes(recipe.getClinicOrgan(), organCodes);
            }
            result.setObject(nameList);
            String showMsg = "由于" + Joiner.on(",").join(nameList) + "门诊药房库存不足，该处方仅支持配送，无法到院取药，是否继续？";
            result.setError(showMsg);
            result.setExtendValue("1");
            result.setCode(RecipeResultBean.FAIL);
            logger.warn("scanDrugStock 存在无库存药品. response={} ", JSONUtils.toString(response));
        }
        logger.info("scanDrugStock 结果={}", JSONObject.toJSONString(result));
        return result;
    }


    public String canOpenRecipeDrugs(Integer clinicOrgan, Integer recipeId, List<Integer> drugIds) {
        List<DrugList> drugList = drugListDAO.findByDrugIds(drugIds);
        //list转map
        Map<Integer, DrugList> drugListMap = drugList.stream().collect(Collectors.toMap(DrugList::getDrugId, a -> a));


        //供应商一致性校验，取第一个药品能配送的药企作为标准
        //应该按照机构配置了的药企作为条件来查找是否能配送
        //获取该机构下配置的药企

        List<DrugsEnterprise> enterprises = organAndDrugsepRelationDAO.findDrugsEnterpriseByOrganIdAndStatus(clinicOrgan, 1);
        List<Integer> deps = enterprises.stream().map(e -> e.getId()).collect(Collectors.toList());
        //找到每一个药能支持的药企关系
        Map<Integer, List<String>> drugDepRel = saleDrugListDAO.findDrugDepRelation(drugIds, deps);

        //无法配送药品校验------有一个药企能支持就不会提示
        List<String> noFilterDrugName = new ArrayList<>();
        for (Integer drugId : drugIds) {
            if (CollectionUtils.isEmpty(drugDepRel.get(drugId))) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            }
        }
        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            logger.warn("setDetailsInfo 存在无法配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}", recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            return Joiner.on(",").join(noFilterDrugName) + "不在该机构可配送药企的药品目录里面，无法进行配送";
        }

        noFilterDrugName.clear();
        //取第一个药能支持的药企做标准来判断
        List<String> firstDrugDepIds = drugDepRel.get(drugIds.get(0));
        for (Integer drugId : drugDepRel.keySet()) {
            List<String> depIds = drugDepRel.get(drugId);
            boolean filterFlag = false;
            for (String depId : depIds) {
                //匹配到一个药企相同则可跳过
                if (firstDrugDepIds.contains(depId)) {
                    filterFlag = true;
                    break;
                }
            }
            if (!filterFlag) {
                noFilterDrugName.add(drugListMap.get(drugId).getDrugName());
            } else {
                //取交集
                firstDrugDepIds.retainAll(depIds);
            }
        }

        if (CollectionUtils.isNotEmpty(noFilterDrugName)) {
            List<DrugList> drugLists = new ArrayList<DrugList>(drugListMap.values());
            List<String> drugNames = drugLists.stream().map(e -> e.getDrugName()).collect(Collectors.toList());
            logger.error("setDetailsInfo 存在无法一起配送的药品. recipeId=[{}], drugIds={}, noFilterDrugName={}", recipeId, JSONUtils.toString(drugIds), JSONUtils.toString(noFilterDrugName));
            //一张处方单上的药品不能同时支持同一家药企配送
            return Joiner.on(",").join(drugNames) + "不支持同一家药企配送";
        }
        return null;
    }


    /**
     * 查询处方无库存药企药品信息
     *
     * @param objects
     * @return
     */
    public List<String> findUnSupportDepList(List<Object> objects) {
        List<List<String>> groupList = new ArrayList<>();
        objects.forEach(a -> {
            List<String> list = (List<String>) a;
            if (CollectionUtils.isNotEmpty(list)) {
                groupList.add(list);
            }
        });
        List<String> drugList = ListValueUtil.minIntersection(groupList);
        logger.info("DrugStockManager findUnSupportDepList  drugList :{}", JSON.toJSONString(drugList));
        return drugList;
    }

}
