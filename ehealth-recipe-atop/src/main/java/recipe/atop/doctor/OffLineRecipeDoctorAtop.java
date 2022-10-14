package recipe.atop.doctor;

import com.google.common.collect.Lists;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.dto.HisRecipeDTO;
import com.ngari.recipe.dto.HisRecipeInfoDTO;
import com.ngari.recipe.recipe.model.*;
import com.ngari.recipe.vo.OffLineRecipeDetailVO;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.springframework.beans.factory.annotation.Autowired;
import recipe.atop.BaseAtop;
import recipe.core.api.patient.IOfflineRecipeBusinessService;

import java.sql.Timestamp;
import java.util.List;
import java.util.stream.Collectors;

/**
 * 线下处方服务入口类
 *
 * @date 2021/8/06
 */
@RpcBean("offLineRecipeAtop")
public class OffLineRecipeDoctorAtop extends BaseAtop {
    @Autowired
    private IOfflineRecipeBusinessService offlineRecipeBusinessService;

    /**
     * 获取线下处方详情
     *
     * @param mpiId       患者ID
     * @param clinicOrgan 机构ID
     * @param recipeCode  处方号码
     * @date 2021/8/06
     */
    @RpcService
    public OffLineRecipeDetailVO getOffLineRecipeDetails(String mpiId, Integer clinicOrgan, String recipeCode) {
        validateAtop(mpiId, clinicOrgan, recipeCode);
        return offlineRecipeBusinessService.getOffLineRecipeDetails(mpiId, clinicOrgan, recipeCode);
    }

    /**
     * 根据处方code 获取线下处方详情
     *
     * @param recipe        患者ID
     * @param recipeDetails 机构ID
     * @date
     */
    @RpcService
    public HisRecipeBean getOffLineRecipeDetailsV1(RecipeBean recipe, List<RecipeDetailBean> recipeDetails) {
        HisRecipeDTO hisRecipeDTO = offlineRecipeBusinessService.getOffLineRecipeDetailsV1(recipe.getClinicOrgan(), recipe.getRecipeCode());
        HisRecipeInfoDTO hisRecipeInfo = hisRecipeDTO.getHisRecipeInfo();
        HisRecipeBean recipeBean = ObjectCopyUtils.convert(hisRecipeInfo, HisRecipeBean.class);
        recipeBean.setSignDate(hisRecipeInfo.getSignTime());
        recipeBean.setCreateDate(Timestamp.valueOf(hisRecipeInfo.getSignTime()));
        recipeBean.setOrganDiseaseName(hisRecipeInfo.getDiseaseName());
        recipeBean.setDepartText(hisRecipeInfo.getDepartName());
        recipeBean.setClinicOrgan(recipe.getClinicOrgan());
        recipeBean.setRecipeExtend(ObjectCopyUtils.convert(hisRecipeDTO.getHisRecipeExtDTO(), RecipeExtendBean.class));
        List<HisRecipeDetailBean> hisRecipeDetailBeans = Lists.newArrayList();
        hisRecipeDTO.getHisRecipeDetail().forEach(a -> {
            HisRecipeDetailBean detailBean = ObjectCopyUtils.convert(a, HisRecipeDetailBean.class);
            detailBean.setDrugUnit(a.getDrugUnit());
            detailBean.setUsingRateText(a.getUsingRate());
            detailBean.setUsePathwaysText(a.getUsePathWays());
            detailBean.setUseDays(null == a.getUseDays() ? null : a.getUseDays().toString());
            detailBean.setUseTotalDose(null != a.getUseTotalDose() ? a.getUseTotalDose().doubleValue() : 0.0);
            detailBean.setUsingRate(a.getUsingRateCode());
            detailBean.setUsePathways(a.getUsePathwaysCode());
            hisRecipeDetailBeans.add(detailBean);
        });
        List<String> organDrugCodeList = recipeDetails.stream().map(RecipeDetailBean::getOrganDrugCode).distinct().collect(Collectors.toList());
        List<HisRecipeDetailBean> recipeDetailBeans = hisRecipeDetailBeans.stream().filter(a -> organDrugCodeList.contains(a.getDrugCode())).collect(Collectors.toList());
        recipeBean.setDetailData(recipeDetailBeans);
        return recipeBean;
    }
}