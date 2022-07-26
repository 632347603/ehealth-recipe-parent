package recipe.core.api;

import com.ngari.platform.recipe.mode.ListOrganDrugReq;
import com.ngari.recipe.drug.model.CommonDrugListDTO;
import com.ngari.recipe.drug.model.DispensatoryDTO;
import com.ngari.recipe.drug.model.SearchDrugDetailDTO;
import com.ngari.recipe.dto.DrugInfoDTO;
import com.ngari.recipe.dto.DrugSpecificationInfoDTO;
import com.ngari.recipe.dto.PatientDrugWithEsDTO;
import com.ngari.recipe.entity.*;
import com.ngari.recipe.vo.DrugSaleStrategyVO;
import com.ngari.recipe.vo.HospitalDrugListReqVO;
import com.ngari.recipe.vo.HospitalDrugListVO;
import com.ngari.recipe.vo.SearchDrugReqVO;
import recipe.vo.patient.PatientContinueRecipeCheckDrugReq;
import recipe.vo.patient.PatientContinueRecipeCheckDrugRes;

import java.util.List;
import java.util.Map;

/**
 * @description： 药品service 接口
 * @author： whf
 * @date： 2021-08-23 19:02
 */
public interface IDrugBusinessService {
    /**
     * 患者端搜索药品信息
     *
     * @param searchDrugReqVo
     * @return
     */
    List<PatientDrugWithEsDTO> findDrugWithEsByPatient(SearchDrugReqVO searchDrugReqVo);

    /**
     * 通过es检索药品
     *
     * @param drugInfo 药品检索条件
     * @param start    开始条数
     * @param limit    每夜条数
     * @return
     */
    List<SearchDrugDetailDTO> searchOrganDrugEs(DrugInfoDTO drugInfo, int start, int limit);

    /**
     * 获取药品说明书
     *
     * @param organId       机构id
     * @param organDrugCode 机构药品编码
     * @return
     */
    Dispensatory getDrugBook(Integer organId, String organDrugCode);


    /**
     * 十八反十九畏的规则
     *
     * @param list
     * @param ruleId
     * @return
     */
    List<RecipeRulesDrugCorrelation> getListDrugRules(List<Integer> list, Integer ruleId);

    /**
     * 查询his 药品说明书
     *
     * @param organId      机构id
     * @param recipeDetail 药品数据
     * @return
     */
    DrugSpecificationInfoDTO hisDrugBook(Integer organId, Recipedetail recipeDetail);

    /**
     * 根据ID获取平台药品列表
     *
     * @param drugIds 平台药品ids
     * @return
     */
    List<DrugList> drugList(List<Integer> drugIds);

    /**
     * 获取机构药品
     *
     * @param organId 机构id
     * @param drugIds 平台药品id
     * @return 机构药品列表
     */
    Map<String, OrganDrugList> organDrugMap(Integer organId, List<Integer> drugIds);

    /**
     * 定时 获取用药提醒的线下处方
     */
    void queryRemindRecipe(String dataTime);

    /**
     * 获取机构药品目录
     *
     * @param listOrganDrugReq
     * @return
     */
    List<OrganDrugList> listOrganDrug(ListOrganDrugReq listOrganDrugReq);

    /**
     * 查询医院药品信息
     * @param hospitalDrugListReqVO
     * @return
     */
    List<HospitalDrugListVO> findHospitalDrugList(HospitalDrugListReqVO hospitalDrugListReqVO);

    /**
     * 查询机构药品信息
     *
     * @param organId
     * @param drugId
     * @return
     */
    DispensatoryDTO getOrganDrugList(Integer organId, Integer drugId);

    /**
     * 查询机构药品数据
     *
     * @param organId
     * @param organDrugCodes
     * @return
     */
    List<OrganDrugList> organDrugList(Integer organId, List<String> organDrugCodes);

    /**
     * 查询机构药品数据
     *
     * @param organId
     * @param organDrugCode
     * @param drugId
     * @return
     */
    OrganDrugList getOrganDrugList(Integer organId, String organDrugCode, Integer drugId);

    /**
     * 查询平台药品
     *
     * @param drugIds
     * @return
     */
    List<DrugList> findByDrugIdsAndStatus(List<Integer> drugIds);

    /**
     * 患者端续方药品信息校验
     *
     * @param patientContinueRecipeCheckDrugReq
     * @return
     */
    PatientContinueRecipeCheckDrugRes patientContinueRecipeCheckDrug(PatientContinueRecipeCheckDrugReq patientContinueRecipeCheckDrugReq);

    /**
     * 查询常用药品
     *
     * @param commonDrug
     * @return
     */
    List<SearchDrugDetailDTO> commonDrugList(CommonDrugListDTO commonDrug);

    /**
     * 查找药品规则
     * @param drugId
     * @param ruleId
     * @return
     */
    List<RecipeRulesDrugCorrelation> findRulesByDrugIdAndRuleId(Integer drugId, Integer ruleId);

    List<RecipeRulesDrugCorrelation> findRulesByCorrelationDrugIdAndRuleId(Integer correlationDrugId, Integer ruleId);

    /**
     * 操作销售策略
     * @param drugSaleStrategy
     */
    void operationDrugSaleStrategy(DrugSaleStrategyVO drugSaleStrategy);

    /**
     * 查找销售策略
     * @param depId
     * @param drugId
     * @return
     */
    List<DrugSaleStrategyVO> findDrugSaleStrategy(Integer depId, Integer drugId);

    List<DrugSaleStrategy> findDrugSaleStrategy(DrugSaleStrategyVO drugSaleStrategy);

    /**
     * 保存药品销售策略
     *
     * @param depId
     * @param drugId
     * @param strategyId
     */
    void saveDrugSaleStrategy(Integer depId, Integer drugId, Integer strategyId);


    /**
     * findSaleDrugListByByDrugIdAndOrganId
     *
     * @param saleDrugList
     * @return
     */
    SaleDrugList findSaleDrugListByDrugIdAndOrganId(SaleDrugList saleDrugList);

    void saveSaleDrugSalesStrategy(SaleDrugList saleDrugList);

}
