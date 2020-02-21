package recipe.service;

import com.google.common.collect.Maps;
import com.ngari.base.searchcontent.model.SearchContentBean;
import com.ngari.base.searchcontent.service.ISearchContentService;
import com.ngari.patient.utils.ObjectCopyUtils;
import com.ngari.recipe.drug.model.DispensatoryDTO;
import com.ngari.recipe.drug.model.DrugListBean;
import com.ngari.recipe.drug.model.SearchDrugDetailDTO;
import com.ngari.recipe.entity.Dispensatory;
import com.ngari.recipe.entity.DrugList;
import ctd.dictionary.DictionaryItem;
import ctd.persistence.DAOFactory;
import ctd.persistence.bean.QueryResult;
import ctd.persistence.exception.DAOException;
import ctd.util.BeanUtils;
import ctd.util.JSONUtils;
import ctd.util.PyConverter;
import ctd.util.annotation.RpcBean;
import ctd.util.annotation.RpcService;
import org.apache.commons.lang3.StringUtils;
import org.apache.log4j.Logger;
import org.joda.time.DateTime;
import recipe.ApplicationUtils;
import recipe.bussutil.RecipeUtil;
import recipe.dao.DispensatoryDAO;
import recipe.dao.DrugListDAO;
import recipe.serviceprovider.BaseService;

import java.util.*;

import static ctd.persistence.DAOFactory.getDAO;

/**
 * Created by zhongzx on 2016/4/28 0028.
 *
 * @author zhongzx
 *         药品服务
 */
@RpcBean("drugListService")
public class DrugListService extends BaseService<DrugListBean> {

    private static Logger logger = Logger.getLogger(DrugListService.class);

    /**
     * 获取热门类目
     * zhongzx
     *
     * @param organId
     * @param drugType
     * @return
     */
    @RpcService
    public List<DictionaryItem> findHotDrugList(int organId, int drugType) {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        List<DrugList> list = drugListDAO.findDrugClassByDrugList(organId, drugType, "", 0, 6);
        List<DictionaryItem> all = drugListExtService.getDrugClass(null, 0);
        List<DictionaryItem> hotItem = new ArrayList<>();

        for (DrugList drugList : list) {
            for (DictionaryItem item : all) {
                if (item.getKey().equals(drugList.getDrugClass())) {
                    hotItem.add(item);
                    break;
                }
            }
        }
        return hotItem;
    }

    /**
     * 把热门类目和一级类目服务包装成一个服务
     * zhongzx
     *
     * @param organId
     * @param drugType
     * @param parentKey
     * @return
     */
    @RpcService
    public HashMap<String, Object> findHotAndFirstDrugList(int organId, int drugType, String parentKey) {
        //热门类目
        List<DictionaryItem> hotList = findHotDrugList(organId, drugType);

        //一级类目
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        List<DictionaryItem> firstList = drugListExtService.findChildByDrugClass(organId, drugType, parentKey);
        HashMap<String, Object> map = Maps.newHashMap();
        map.put("hot", hotList);
        map.put("first", firstList);
        return map;
    }

    /**
     * 添加药品
     *
     * @param d
     * @return
     * @author zhongzx
     */
    @RpcService
    public DrugListBean addDrugList(DrugListBean drugListBean) {
        DrugList d = ObjectCopyUtils.convert(drugListBean, DrugList.class);
        logger.info("新增药品服务[addDrugList]:" + JSONUtils.toString(d));
        if (null == d) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugList is null");
        }
        DrugListDAO dao = getDAO(DrugListDAO.class);
        //根据saleName 判断改药品是否已添加
        if (StringUtils.isEmpty(d.getDrugName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugName is needed");
        }
        if (StringUtils.isEmpty(d.getSaleName())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "saleName is needed");
        }
        if (null == d.getPrice1()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "price1 is needed");
        }
        if (null == d.getPrice2()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "price2 is needed");
        }
        if (null == d.getDrugType()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugType is needed");
        }
        if (StringUtils.isEmpty(d.getDrugClass())) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugClass is needed");
        }
        if (null == d.getStatus()) {
            d.setStatus(1);
        }
        d.setCreateDt(new Date());
        d.setLastModify(new Date());
        d.setAllPyCode(PyConverter.getPinYinWithoutTone(d.getSaleName()));
        d.setPyCode(PyConverter.getFirstLetter(d.getSaleName()));
        d = dao.save(d);

        if(null != drugListBean.getDispensatory()) {
            DispensatoryDAO dispensatoryDAO = DAOFactory.getDAO(DispensatoryDAO.class);
            DispensatoryDTO dispensatoryDTO = drugListBean.getDispensatory();
            dispensatoryDTO.setDrugId(d.getDrugId());
            dispensatoryDTO.setDrugName(d.getDrugName());
            dispensatoryDTO.setSaleName(d.getSaleName());
            dispensatoryDTO.setSpecs(d.getDrugSpec());
            Date now = DateTime.now().toDate();
            dispensatoryDTO.setCreateTime(now);
            dispensatoryDTO.setLastModifyTime(now);

            dispensatoryDAO.save(ObjectCopyUtils.convert(dispensatoryDTO, Dispensatory.class));
        }

        return getBean(d, DrugListBean.class);
    }

    /**
     * 给没有拼音全码的加上拼音全码
     */
    @RpcService
    public void setAllPyCode() {
        DrugListDAO dao = getDAO(DrugListDAO.class);
        List<DrugList> list = dao.findAll();
        for (DrugList d : list) {
            if (StringUtils.isEmpty(d.getAllPyCode())) {
                d.setAllPyCode(PyConverter.getPinYinWithoutTone(d.getSaleName()));
                dao.update(d);
            }
        }
    }

    //给所有药物加上拼音简码
    @RpcService
    public void setPyCode() {
        DrugListDAO dao = getDAO(DrugListDAO.class);
        List<DrugList> list = dao.findAll();
        for (DrugList d : list) {
            if (StringUtils.isEmpty(d.getPyCode())) {
                d.setPyCode(PyConverter.getFirstLetter(d.getSaleName()));
                dao.update(d);
            }
        }
    }

    @RpcService
    public boolean isExistDrugId(Integer drugId){
        if (drugId == null) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is required");
        }
        DrugListDAO dao = getDAO(DrugListDAO.class);
        DrugList drugList = dao.getById(drugId);
        if (drugList == null) {
            return false;
        } else {
            return true;
        }
    }

    /**
     * 更新药品信息
     *
     * @param drugList
     * @return
     * @author zhongzx
     */
    @RpcService
    public DrugListBean updateDrugList(DrugListBean drugListBean) {
        DrugList drugList = ObjectCopyUtils.convert(drugListBean, DrugList.class);
        logger.info("修改药品服务[updateDrugList]:" + JSONUtils.toString(drugList));
        DrugListDAO dao = getDAO(DrugListDAO.class);
        if (null == drugList.getDrugId()) {
            throw new DAOException(DAOException.VALUE_NEEDED, "drugId is required");
        }
        DrugList target = dao.getById(drugList.getDrugId());
        if (null == target) {
            throw new DAOException(DAOException.ENTITIY_NOT_FOUND, "Can't found drugList");
        } else {
            drugList.setLastModify(new Date());
            if(null == drugList.getAllPyCode()){
                drugList.setAllPyCode(target.getAllPyCode());
            }
            if(null == drugList.getApprovalNumber()){
                drugList.setApprovalNumber(target.getApprovalNumber());
            }
            if(null == drugList.getBaseDrug()){
                drugList.setBaseDrug(target.getBaseDrug());
            }
            if(null == drugList.getCreateDt()){
                drugList.setCreateDt(target.getCreateDt());
            }
            if(null == drugList.getDrugClass()){
                drugList.setDrugClass(target.getDrugClass());
            }
            if(null == drugList.getDrugForm()){
                drugList.setDrugForm(target.getDrugForm());
            }
            if(null == drugList.getDrugId()){
                drugList.setDrugId(target.getDrugId());
            }
            if(null == drugList.getDrugName()){
                drugList.setDrugName(target.getDrugName());
            }
            if(null == drugList.getDrugPic()){
                drugList.setDrugPic(target.getDrugPic());
            }
            if(null == drugList.getDrugSpec()){
                drugList.setDrugSpec(target.getDrugSpec());
            }
            if(null == drugList.getDrugType()){
                drugList.setDrugType(target.getDrugType());
            }
            if(null == drugList.getHighlightedField()){
                drugList.setHighlightedField(target.getHighlightedField());
            }
            if(null == drugList.getHighlightedFieldForIos()){
                drugList.setHighlightedFieldForIos(target.getHighlightedFieldForIos());
            }
            if(null == drugList.getHospitalPrice()){
                drugList.setHospitalPrice(target.getHospitalPrice());
            }
            if(null == drugList.getIndications()){
                drugList.setIndications(target.getIndications());
            }
            if(null == drugList.getLastModify()){
                drugList.setLastModify(target.getLastModify());
            }
            if(null == drugList.getOrganDrugCode()){
                drugList.setOrganDrugCode(target.getOrganDrugCode());
            }
            if(null == drugList.getPack()){
                drugList.setPack(target.getPack());
            }
            if(null == drugList.getPrice1()){
                drugList.setPrice1(target.getPrice1());
            }
            if(null == drugList.getPrice2()){
                drugList.setPrice2(target.getPrice2());
            }
            if(null == drugList.getPyCode()){
                drugList.setPyCode(target.getPyCode());
            }
            if(null == drugList.getSaleName()){
                drugList.setSaleName(target.getSaleName());
            }
            if(null == drugList.getSourceOrgan()){
                drugList.setSourceOrgan(target.getSourceOrgan());
            }
            if(null == drugList.getStandardCode()){
                drugList.setStandardCode(target.getStandardCode());
            }
            if(null == drugList.getStatus()){
                drugList.setStatus(target.getStatus());
            }
            if(null == drugList.getUnit()){
                drugList.setUnit(target.getUnit());
            }
            if(null == drugList.getUseDose()){
                drugList.setUseDose(target.getUseDose());
            }
            if(null == drugList.getUseDoseUnit()){
                drugList.setUseDoseUnit(target.getUseDoseUnit());
            }
            if(null == drugList.getUsePathways()){
                drugList.setUsePathways(target.getUsePathways());
            }
            if(null == drugList.getUsingRate()){
                drugList.setUsingRate(target.getUsingRate());
            }
            if(null == drugList.getProducer()){
                drugList.setProducer(target.getProducer());
            }
            if(null == drugList.getInstructions()){
                drugList.setInstructions(target.getInstructions());
            }

           /*BeanUtils.map(drugList, target);*/
            drugList = dao.update(drugList);
            if(null != drugListBean.getDispensatory()) {
                DispensatoryDAO dispensatoryDAO = DAOFactory.getDAO(DispensatoryDAO.class);
                Dispensatory dispensatory = dispensatoryDAO.getByDrugId(drugList.getDrugId());
                dispensatory.setLastModifyTime(new Date());
                BeanUtils.map(drugListBean.getDispensatory(), dispensatory);
                dispensatoryDAO.update(dispensatory);
            }
        }
        return getBean(drugList, DrugListBean.class);
    }

    /**
     * 运营平台 药品查询服务
     *
     * @param drugClass 药品分类
     * @param status    药品状态
     * @param keyword   查询关键字:药品名称 or 生产厂家 or 商品名称 or 批准文号 or drugId
     * @param start     分页起始位置
     * @param limit     每页限制条数
     * @return QueryResult<DrugList>
     * @author houxr
     */
    @RpcService
    public QueryResult<DrugListBean> queryDrugListsByDrugNameAndStartAndLimit(final String drugClass, final String keyword,
                                                                              final Integer status,
                                                                              final int start, final int limit) {
        DrugListDAO dao = getDAO(DrugListDAO.class);
        QueryResult result = dao.queryDrugListsByDrugNameAndStartAndLimit(drugClass, keyword, status, start, limit);
        List<DrugListBean> list = getList(result.getItems(), DrugListBean.class);
        result.setItems(list);
        return result;
    }

    /**
     * 患者端 获取对应机构的西药 或者 中药的药品有效全目录（现在目录有二级）
     *
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<Map<String, Object>> queryDrugCatalog() {
        List<Map<String, Object>> returnList = new ArrayList<>();
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        //先获得一级有效类目
        List<DictionaryItem> firstList = drugListExtService.findChildByDrugClass(null, null, "");

        //再获取二级有效目录
        for (DictionaryItem first : firstList) {
            List<DictionaryItem> childList = drugListExtService.findChildByDrugClass(null, null, first.getKey());
            HashMap<String, Object> map = Maps.newHashMap();
            map.put("key", first.getKey());
            map.put("text", first.getText());
            map.put("leaf", first.isLeaf());
            map.put("index", first.getIndex());
            map.put("mcode", first.getMCode());
            map.put("child", childList);
            returnList.add(map);
        }
        return returnList;
    }


    /**
     * 医生端 获取有效药品目录
     *
     * @param organId  机构平台编号
     * @param drugType 药品类型 1-西药 2-中成药
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<Map<String, Object>> queryDrugCatalogForDoctor(int organId, int drugType) {
        List<Map<String, Object>> returnList = new ArrayList<>();
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        //先获得一级有效类目
        List<DictionaryItem> firstList = drugListExtService.findChildByDrugClass(organId, drugType, "");

        //再获取二级有效目录
        for (DictionaryItem first : firstList) {
            List<DictionaryItem> childList = drugListExtService.findChildByDrugClass(organId, drugType, first.getKey());
            HashMap<String, Object> map = Maps.newHashMap();
            map.put("key", first.getKey());
            map.put("text", first.getText());
            map.put("leaf", first.isLeaf());
            map.put("index", first.getIndex());
            map.put("mcode", first.getMCode());
            map.put("child", childList);
            returnList.add(map);
        }
        return returnList;
    }

    /**
     * 患者端 推荐药品列表
     *
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<DrugListBean> recommendDrugList(int start, int limit) {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        List<DrugList> drugList = drugListDAO.queryDrugList(start, limit);
        return getList(drugList, DrugListBean.class);
    }

    /**
     * 患者端 查询某个二级药品目录下的药品列表
     *
     * @param drugClass 药品二级目录
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<DrugListBean> queryDrugsInDrugClass(String drugClass, int start, int limit) {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        //  2017/3/13 0013  因为梅州药品的原因 患者端 写死查询邵逸夫的药品
        List<DrugList> drugList = drugListDAO.findDrugListsByOrganOrDrugClass(1, null, drugClass, start, limit);
        return getList(drugList, DrugListBean.class);
    }

    /**
     * 患者端 药品搜索服务 药品名 商品名 拼音 别名
     *
     * @param drugName 搜索的文字或者拼音
     * @param start
     * @param limit
     * @return
     * @author zhongzx
     */
    @RpcService
    public List<SearchDrugDetailDTO> searchDrugByNameOrPyCode(String drugName, String mpiId, int start, int limit) {
        saveSearchContendForDrug(drugName, mpiId);
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        //因为 梅州药品的原因 患者端 写死查询邵逸夫的药品
        return drugListExtService.searchDrugListWithES(null, null, drugName, start, limit);
    }

    /**
     * PC端 药品搜索
     *
     * @param organId
     * @param drugType
     * @param drugName
     * @param start
     * @return
     */
    @RpcService
    public List<SearchDrugDetailDTO> searchDrugByNameOrPyCodeForPC(
            final int organId, final int drugType, final String drugName, final int start, final int limit) {
        DrugListExtService drugListExtService = ApplicationUtils.getRecipeService(DrugListExtService.class, "drugList");
        return drugListExtService.searchDrugListWithES(organId, drugType, drugName, start, limit);
    }

    @RpcService
    public void saveSearchContendForDrug(String drugName, String mpiId) {
        /**
         * 保存患者搜索记录(药品，医生)
         */
        if (!StringUtils.isEmpty(drugName) && !StringUtils.isEmpty(mpiId)) {
            ISearchContentService iSearchContentService = ApplicationUtils.getBaseService(ISearchContentService.class);
            SearchContentBean content = new SearchContentBean();
            content.setMpiId(mpiId);
            content.setContent(drugName);
            content.setBussType(2);
            content.setSubType(2);
            iSearchContentService.addSearchContent(content, 0);
        }
    }

    /**
     * 更新药品首字母拼音字段--更换成商品名首字母拼音
     *
     * @return
     * @author zhongzx
     */
    @RpcService
    public boolean updatePyCode() {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        List<DrugList> drugLists = drugListDAO.findAll();
        Map<String, Object> changeAttr = Maps.newHashMap();
        for (DrugList drugList : drugLists) {
            String pyCode = PyConverter.getFirstLetter(drugList.getSaleName());
            changeAttr.put("pyCode", pyCode);
            drugListDAO.updateDrugListInfoById(drugList.getDrugId(), changeAttr);
        }
        return true;
    }

    /**
     * 三级目录改成二级目录，更改 三级目录药品 到对应的二级目录
     *
     * @return
     * @author zhongzx
     */
    @RpcService
    public boolean updateDrugCatalog() {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        List<DrugList> dList = drugListDAO.findAll();
        Map<String, Object> changeAttr = Maps.newHashMap();
        for (DrugList drugList : dList) {
            String drugClass = drugList.getDrugClass();
            if (6 == drugClass.length()) {
                drugClass = drugClass.substring(0, 4);
                changeAttr.put("drugClass", drugClass);
                drugListDAO.updateDrugListInfoById(drugList.getDrugId(), changeAttr);
            }
        }
        return true;
    }

    /**
     * PC端搜索药品，药品参数为空时展示该机构的药品列表
     *
     * @param organId
     * @param drugType
     * @param drugClass
     * @param start
     * @param limit
     * @return
     */
    @RpcService
    public List<DrugListBean> findAllInDrugClassByOrganForPC(int organId, int drugType,
                                                             String drugClass, int start, int limit) {
        DrugListDAO drugListDAO = getDAO(DrugListDAO.class);
        List<DrugList> dList = drugListDAO.findDrugListsByOrganOrDrugClass(organId, drugType, drugClass, start,
                limit);
        // 添加医院价格
        if (!dList.isEmpty()) {
            RecipeUtil.getHospitalPrice(organId, dList);
        }
        return getList(dList, DrugListBean.class);
    }
}
