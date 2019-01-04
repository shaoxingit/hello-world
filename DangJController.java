package com.jeeplus.front;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.activiti.engine.impl.util.json.JSONObject;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.jeeplus.common.config.Global;
import com.jeeplus.common.mapper.JsonMapper;
import com.jeeplus.common.persistence.Page;
import com.jeeplus.common.utils.StringUtils;
import com.jeeplus.modules.info.entity.CodeActivityType;
import com.jeeplus.modules.info.entity.InfoActivity;
import com.jeeplus.modules.info.entity.InfoActivityApply;
import com.jeeplus.modules.info.entity.InfoCust;
import com.jeeplus.modules.info.service.CodeActivityTypeService;
import com.jeeplus.modules.info.service.InfoActivityApplyService;
import com.jeeplus.modules.info.service.InfoActivityService;
import com.jeeplus.modules.info.service.InfoCustService;
import com.jeeplus.modules.party.entity.PartyExamQuestion;
import com.jeeplus.modules.party.entity.PartyExamRecord;
import com.jeeplus.modules.party.entity.PartyRecordDetail;
import com.jeeplus.modules.party.service.PartyExamQuestionService;
import com.jeeplus.modules.party.service.PartyExamRecordService;
import com.jeeplus.modules.party.service.PartyRecordDetailService;
import com.jeeplus.modules.sys.service.SystemService;

@Controller
@RequestMapping(value="${dangjPath}")
public class DangJController {
	private static final String APPID="wxcdd167d73a486e7b";
	private static final String APPSECRET="e0d3dfe7520996b0e8c6275f8a21aaef";
	private static final String redirect_uri=Global.getConfig("cas.project.url")+Global.getDangjPath()+"/judge";
	
	@Autowired
	private InfoCustService infoCustService;
	@Autowired
	private InfoActivityService infoActivityService;
	@Autowired
	private InfoActivityApplyService infoActivityApplyService;
	@Autowired
	private PartyExamQuestionService partyExamQuestionService;
	@Autowired
	private PartyExamRecordService partyExamRecordService;
	@Autowired
	private PartyRecordDetailService partyRecordDetailService;

	/**
	 * type(act活动/exam答题) 
	 */
	@RequestMapping(value = "auth")
	public String auth(HttpServletRequest request, HttpServletResponse response,String type,String actType) {
		
		 return "redirect:"+String.format("https://open.weixin.qq.com/connect/oauth2/authorize?appid=%s&redirect_uri=%s&response_type=code&scope=%s&state=%s#wechat_redirect",
				 APPID, redirect_uri+"-"+type+"-"+actType, "snsapi_userinfo", "xxxx_state");
	}
	
	
	/**
	 * type(act活动/exam答题)  默认答题
	 */
	
	@RequestMapping(value = "judge-{type}-{actType}")
	public String judge(HttpServletRequest request, @RequestParam("code") String code,Model model,@PathVariable String type,@PathVariable String actType) {
	
		/* Map<String, String> data = new HashMap();*/
	     Map<String, String> result = getUserInfoAccessToken(code);//通过这个code获取access_token
	        String openId = result.get("openid");
		
			/*String openId=request.getParameter("openId");*/
	        if (StringUtils.isNotEmpty(openId)) {
	        	/*Map<String, String> userInfo = getUserInfo(result.get("access_token"), openId);//使用access_token获取用户信息
	            System.out.println("received user info. [result={}]"+ userInfo);*/
	           InfoCust infoCust=new InfoCust();
	           infoCust.setOpenId(openId);
	           List<InfoCust> custList=infoCustService.findList(infoCust);
	           if(custList.size()>0) {
	        	   //微信绑定过警号
	        	   request.getSession().setAttribute("wxCust",custList.get(0));
	        	   if("act".equals(type)) {
	        		 //跳转活动列表
		        	   return "redirect:"+Global.getDangjPath()+"/actList?type="+actType;  
	        	   }else {
	        		   return "dangj/game/shouye";  
	        	   }
	        }else {
	        	   model.addAttribute("openId",openId);
	        	   model.addAttribute("type",type);
	        	   //跳转绑定警号页面
	        	   return "dangj/bind";
	        }
	        }else {
	        	return "";
	        }
	 }
	
	/**
	 * 微信绑定警号
	 * @param request
	 * @param response
	 * @param policeNo
	 * @param type
	 * @param openId
	 */
	@RequestMapping(value = "bind")
	public void bind(HttpServletRequest request,HttpServletResponse response,String policeNo,String type,String openId,String password) {
		JSONObject json=new JSONObject();
		InfoCust infoCust=new InfoCust();
		infoCust.setPoliceNo(policeNo);
		//根据警号查询警号信息
		List<InfoCust> list=infoCustService.findList(infoCust);
		//判断是否存在
		if(list.size()>0) {
			//判断密码是否正确
			boolean isPass = SystemService.validatePassword(password, list.get(0).getPassword());
			if(isPass) {
				infoCust=list.get(0);
				//判断是否允许参加党建活动
				if("1".equals(infoCust.getMarry())) {
					json.put("flag",true);
					//判断该警员是否已经绑定过微信
					if(infoCust.getOpenId()==null||"".equals(infoCust.getOpenId())) {
						infoCust.setOpenId(openId);
						infoCustService.save(infoCust);
						request.getSession().setAttribute("wxCust",infoCust);
						json.put("msg","绑定成功");
					}else {
						json.put("msg",infoCust.getName()+",您已经绑定过其他的微信号，确定要重新绑定该微信号？");
						json.put("judge","true");
						json.put("id",infoCust.getId());
					}
				}else {
					json.put("flag",false);
					json.put("msg","该警员尚未被允许参加党建活动！");	
				}
				
			}else {
				json.put("flag",false);
				json.put("msg","密码错误，请重试");
			}
			
		}else {
			json.put("flag",false);
			json.put("msg","系统中尚未录入该警员，请联系管理员录入");
		}
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	
	/**
	 * 保存openId
	 * @param request
	 * @param response
	 * @param id
	 * @param openId
	 */
	@RequestMapping(value = "saveOpenId")
	public void saveOpenId(HttpServletRequest request,HttpServletResponse response,String id,String openId) {
		JSONObject json=new JSONObject();
		InfoCust infoCust=new InfoCust();
		infoCust.setId(id);
		infoCust=infoCustService.get(infoCust);
		infoCust.setOpenId(openId);
		//保存微信openid
		infoCustService.save(infoCust);
		request.getSession().setAttribute("wxCust",infoCust);
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	/**
	 * 活动列表
	 * @param request
	 * @param model
	 * @param index
	 * @param type
	 * @return
	 */
	@RequestMapping(value = "actList")
	public String actList(HttpServletRequest request,Model model,Integer index,String type) {
		//判断session是否失效
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=act&actType=1";
		}
		//默认第一页
		index=index==null?1:index;
		Page<InfoActivity> page=new Page<InfoActivity>(index,6);
		page.setOrderBy("a.create_date desc");
		InfoActivity infoActivity=new InfoActivity();
		//默认管理员发布的活动
		if(type==null||"".equals(type)) {
			type="1";
		}
		
		if("2".equals(type)) {
		//我自己提交审核的活动
			infoActivity.setCreatePerson(cust.getId());
		}
		infoActivity.setType(type);
		//查询活动列表
		page=infoActivityService.findPage(page, infoActivity);
		model.addAttribute("pagger", page);
		model.addAttribute("type",type);
		return "dangj/actList";
	 }
	/**
	 * 活动列表上拉加载（分页）
	 * @param request
	 * @param response
	 * @param index
	 * @param type
	 * @throws IOException
	 */
	@RequestMapping(value="actPage")
	public void actPage(HttpServletRequest request,HttpServletResponse response,Integer index,String type) throws IOException {
		//判断session是否失效
		JSONObject json=new JSONObject();
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		
		if(cust==null) {
			json.put("ifLogin", true);
			json.put("url", Global.getDangjPath()+"/auth?type=act&actType=1");
		}else {
			InfoActivity infoActivity=new InfoActivity();
			//默认管理员发布的活动
			if(type==null||"".equals(type)) {
				type="1";
			}
			//自己提交审核的活动
			if("2".equals(type)) {
				infoActivity.setCreatePerson(cust.getId());
			}
			infoActivity.setType(type);
			//默认加载第二页
			index=(index==null||index==0)?2:index;
			Page<InfoActivity> page=new Page<InfoActivity>(index,6);
			page.setOrderBy("a.create_date desc");
			page=infoActivityService.findPage(page,infoActivity );
			//防止活动内容过长，手动截取活动内容
			for(InfoActivity entity:page.getList()) {
				entity.setActivityContent(StringUtils.abbr(infoActivity.getActivityContent(), 90));
			}
			if(page.getPageCount()<index) {
				json.put("list", JsonMapper.getInstance().toJson(new ArrayList<InfoCust>()));
				json.put("size", 0);
			}else {
				json.put("list", JsonMapper.getInstance().toJson(page.getList()));
				json.put("size", page.getList().size());
			}
		}
		
		response.getWriter().println(json);;
	}
	/**
	 * 活动详情
	 * @param request
	 * @param model
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "actDetail")
	public String actDetail(HttpServletRequest request,Model model,String id) {
		//判断session是否失效
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=act&actType=1";
		}
		//获取活动信息
		 model.addAttribute("infoActivity",infoActivityService.get(id));
		 InfoActivityApply infoActivityApply=new InfoActivityApply();
			infoActivityApply.setCustId(cust.getId());
			infoActivityApply.setActivityId(id);
			//获取活动报名信息
			List<InfoActivityApply> infoActivityApplyList= infoActivityApplyService.findList(infoActivityApply);
			model.addAttribute("size", infoActivityApplyList.size());
			if( infoActivityApplyList.size()>0) {
				model.addAttribute("infoActivityApply", infoActivityApplyList.get(0));
			}
		return "dangj/actDetail";
	 }
	
	/**
	 * 活动签到，默认5分
	 * @param request
	 * @param response
	 * @param model
	 * @param id
	 */
	@RequestMapping(value = "sign")
	public void sign(HttpServletRequest request,HttpServletResponse response,Model model,String id,String address,String lng,String lat) {
	//判断session是否失效
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		JSONObject json=new JSONObject();
		if(cust==null) {
			json.put("flag", false);
			json.put("url", Global.getDangjPath()+"/auth?type=act&actType=1");
		}else {
			//获取本场活动签到信息
			InfoActivityApply infoActivityApply=new InfoActivityApply();
			infoActivityApply.setCustId(cust.getId());
			infoActivityApply.setActivityId(id);
			List<InfoActivityApply> infoActivityApplyList= infoActivityApplyService.findList(infoActivityApply);
			//只能签到一次无法重复签到
			if(infoActivityApplyList.size()>0) {
				json.put("flag", false);
				json.put("msg", "您已签到，请勿重复签到");
			}else {
				//没有签到，保存签到信息
				json.put("flag", true);
				infoActivityApply.setPoliceNo(cust.getPoliceNo());
				infoActivityApply.setStateId("1");
				infoActivityApply.setAddDate(new Date());
				infoActivityApply.setAddress(address);
				infoActivityApply.setLng(lng);
				infoActivityApply.setLat(lat);
				infoActivityApplyService.save(infoActivityApply);
				json.put("msg", "签到成功");
				/*//获取活动信息
				InfoActivity infoActivity=infoActivityService.get(id);
				int score=5;
				if(infoActivity!=null&&infoActivity.getActivityType()!=null&&!"".equals(infoActivity.getActivityType())) {
					CodeActivityType codeActivityType=codeActivityTypeService.get(infoActivity.getActivityType());
					if(codeActivityType!=null) {
						score=codeActivityType.getScore();
					}
				}
				//获取活动类型
				cust.setPartyScore(cust.getPartyScore()==null?score:(cust.getPartyScore()+score));
				cust.setActScore(cust.getActScore()==null?score:(cust.getActScore()+score));
				infoCustService.save(cust);
				request.getSession().setAttribute("wxCust",cust);
				json.put("msg", "签到成功，获得活动积分"+score+"分");*/
			}
		}
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	
	
	@RequestMapping(value = "actApply")
	public String actApply(HttpServletRequest request,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=act&actType=1";
		}
		return "dangj/actApply";
	 }
	
	/**
	 * 活动签到，一次5分
	 * @param request
	 * @param response
	 * @param model
	 * @param id
	 */
	@RequestMapping(value = "actApplySave")
	public void actApplySave(HttpServletRequest request,HttpServletResponse response,InfoActivity infoActivity) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		JSONObject json=new JSONObject();
		if(cust==null) {
			json.put("flag", false);
			json.put("url", Global.getDangjPath()+"/auth?type=act&actType=1");
		}else {
			infoActivity.setCreatePerson(cust.getId());
			infoActivity.setUpdatePerson(cust.getId());
			infoActivity.setState("1");
			infoActivity.setType("2");
			infoActivityService.save(infoActivity);
			json.put("flag", true);
			json.put("msg", "活动提交成功，请耐心等待管理员审核");
		}
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	
	/**
	 * 党建知识竞答首页
	 * @param request
	 * @param model
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "game/shouye")
	public String shouye(HttpServletRequest request,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=exam";
		}
		 
		return "dangj/game/shouye";
	 }
	
	/**
	 * 党建知识竞答首页ifram
	 * @param request
	 * @param model
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "game/index")
	public String index(HttpServletRequest request,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=exam";
		}
		 
		return "dangj/game/index";
	 }
	
	/**
	 * 答题规则
	 * @param request
	 * @param model
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "game/rule")
	public String rule(HttpServletRequest request,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=exam";
		}
		 
		return "dangj/game/rule";
	 }
	
	/**
	 * 提示答题信息
	 * 默认每月前三次答题获得积分
	 * @param request
	 * @param response
	 * @param model
	 * @param id
	 */
	@RequestMapping(value = "judgeExam")
	public void judgeExam(HttpServletRequest request,HttpServletResponse response,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		JSONObject json=new JSONObject();
		if(cust==null) {
			json.put("flag", false);
			json.put("ifLogin", true);
			json.put("url", Global.getDangjPath()+"/auth?type=exam");
		}else {
			//日历设置当前时间，获取年，月
			Date date = new Date();
			Calendar cal = Calendar.getInstance();
			cal.setFirstDayOfWeek(Calendar.MONDAY);
			cal.setTime(date);
			int month= cal.get(Calendar.MONTH);
			int year=cal.get(Calendar.YEAR);
			PartyExamRecord partyExamRecord=new PartyExamRecord();
			partyExamRecord.setCustId(cust.getId());
			partyExamRecord.setYear(year+"");
			partyExamRecord.setWeek(month+"");
			//查询本月党建答题记录
			List<PartyExamRecord> list=partyExamRecordService.findList(partyExamRecord);
			json.put("flag", true);
			//考试信息提示
			if(list.size()<2) {
				json.put("msg", "本月第"+(list.size()+1)+"次答题，答对一题得一分");
			}else if(list.size()==2) {
				json.put("msg", "本月第3次答题，答对一题得两分");
			}else{
				json.put("msg", "您本月已经答过三次，本月接下来的答题将不再获得积分");
				
			}
			//保存答题记录
			partyExamRecord.setPoliceNo(cust.getPoliceNo());
			partyExamRecordService.save(partyExamRecord);
			//删除答题记录详情
			partyRecordDetailService.deleteByCustId(cust.getId());
			//插入答题记录详情
			Map<Object,Object> map=new HashMap<Object,Object>();
			map.put("custId", cust.getId());
			partyRecordDetailService.insertAll(map);
		}
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	
	
	/**
	 * 答题
	 * @param request
	 * @param model
	 * @param id
	 * @return
	 */
	@RequestMapping(value = "game/exam")
	public String exam(HttpServletRequest request,Model model,String id) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		if(cust==null) {
			return "redirect:"+Global.getDangjPath()+"/auth?type=exam";
		}
		 Map<Object,Object> map=new HashMap<Object,Object>();
		 map.put("custId", cust.getId());
		 //查询题目
		 PartyExamQuestion partyExamQuestion=partyExamQuestionService.getPartyQuestion(map);
		 model.addAttribute("partyExamQuestion", partyExamQuestion);
		 //查询第几道题
		 PartyRecordDetail partyRecordDetail=new PartyRecordDetail();
		 partyRecordDetail.setCustId(cust.getId());
		 int num=partyRecordDetailService.findList(partyRecordDetail).size();
		 if(num==0) {
//			 查询当前月答题记录
			 Date date = new Date();
				Calendar cal = Calendar.getInstance();
				cal.setFirstDayOfWeek(Calendar.MONDAY);
				cal.setTime(date);
				int month= cal.get(Calendar.MONTH);
				int year=cal.get(Calendar.YEAR);
				PartyExamRecord partyExamRecord=new PartyExamRecord();
				partyExamRecord.setCustId(cust.getId());
				partyExamRecord.setYear(year+"");
				partyExamRecord.setWeek(month+"");
				//获取最新一场记录
				List<PartyExamRecord> list=partyExamRecordService.findList(partyExamRecord);
				partyExamRecord=list.get(0);
				model.addAttribute("partyExamRecord", partyExamRecord);
				model.addAttribute("size", list.size());
			 return "dangj/game/result";
		 }
		 String [] numArry= {"一","二","三","四","五"};
		 //获取题号
		 model.addAttribute("no",numArry[5-num]);
		return "dangj/game/exam";
	 }
	
	/**
	 * 下一题
	 * @param request
	 * @param response
	 * @param model
	 * @param id
	 */
	@RequestMapping(value = "next")
	public void next(HttpServletRequest request,HttpServletResponse response,Model model,String questionId,String answer,String detailId) {
		InfoCust cust=(InfoCust)request.getSession().getAttribute("wxCust");
		JSONObject json=new JSONObject();
		if(cust==null) {
			json.put("flag", false);
			json.put("ifLogin", true);
			json.put("url", Global.getDangjPath()+"/auth?type=exam");
		}else {
			PartyRecordDetail partyRecordDetail=partyRecordDetailService.get(detailId);
			json.put("flag", true);
			if(partyRecordDetail!=null) {
				//删除本题记录
				partyRecordDetailService.delete(partyRecordDetail);
				//获取本题信息
				PartyExamQuestion partyExamQuestion=partyExamQuestionService.get(questionId);
				//查询该党员本月答题记录
				Date date = new Date();
				Calendar cal = Calendar.getInstance();
				cal.setFirstDayOfWeek(Calendar.MONDAY);//设置周一为一周的第一天
				cal.setTime(date);
				int month= cal.get(Calendar.MONTH);
				int year=cal.get(Calendar.YEAR);
				PartyExamRecord partyExamRecord=new PartyExamRecord();
				partyExamRecord.setCustId(cust.getId());
				partyExamRecord.setYear(year+"");
				partyExamRecord.setWeek(month+"");
				List<PartyExamRecord> list=partyExamRecordService.findList(partyExamRecord);
				//获取当前答题记录
				partyExamRecord=list.get(0);
				partyExamRecord.setAnswerNum(Integer.parseInt(partyExamRecord.getAnswerNum())+1+"");
				if(partyExamQuestion.getAnswer().equals(answer)) {
					json.put("ifCorrect", true);
					
					partyExamRecord.setCorrectNum(Integer.parseInt(partyExamRecord.getCorrectNum())+1+"");
					// 如果答题两次以内答对一题一分
					if(list.size()<=2) {
						cust.setPartyScore(cust.getPartyScore()==null?1:(cust.getPartyScore()+1));
						cust.setGameScore(cust.getGameScore()==null?1:(cust.getGameScore()+1));
						infoCustService.save(cust);
						request.getSession().setAttribute("wxCust",cust);
					}else if(list.size()==3) {
						//答题第三次答对一题2分
						cust.setPartyScore(cust.getPartyScore()==null?2:(cust.getPartyScore()+2));
						cust.setGameScore(cust.getGameScore()==null?2:(cust.getGameScore()+2));
						infoCustService.save(cust);
						request.getSession().setAttribute("wxCust",cust);
					}
					//超过三次不得分
				}else {
					json.put("ifCorrect", false);
				}
				partyExamRecordService.save(partyExamRecord);
			}else {
				json.put("ifCorrect", false);
			}
			
		}
		try {
			response.getWriter().println(json);
		} catch (IOException e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	 }
	
	/**
	 * ******************************************************排行榜******************************************************
	 */
	
	/**
	 *  积分排行榜
	 */
	
	@RequestMapping(value="phb")
	public String phb(Model model,HttpServletRequest request,Integer index,String orderBy) {
		//按照总积分查询
		index=index==null?1:index;
		//设置党员
		InfoCust infoCust=new InfoCust();
		infoCust.setMarry("1");
		Date date = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.MONTH, -1);
		int month= cal.get(Calendar.MONTH);
		int year=cal.get(Calendar.YEAR);
		infoCust.setYear(year+"");
		infoCust.setMonth(month+"");
		Page<InfoCust> page=new Page<InfoCust>(index, 15);
		//默认总分
		orderBy=(orderBy==null||"".equals(orderBy))?"a.party_score":orderBy;
		page.setOrderBy(orderBy+" desc");
		page=infoCustService.findPartyPage(page, infoCust);
	//判断积分显示类型
		for(InfoCust cust:page.getList()) {
			if("a.party_score".equals(orderBy)) {
				cust.setScore(cust.getPartyScore());
			}else if("a.act_score".equals(orderBy)) {
				cust.setScore(cust.getActScore());
			}else if("a.game_score".equals(orderBy)) {
				cust.setScore(cust.getGameScore());
			}
		}
		model.addAttribute("page",page);
		model.addAttribute("size", page.getList().size());
		model.addAttribute("orderBy", orderBy);
		return "dangj/phb";
	}
	/**
	 * 党建排行榜下拉加载（分页）
	 * @param model
	 * @param response
	 * @param index
	 * @param orderBy
	 * @throws IOException
	 */
	@RequestMapping(value="phbPage")
	public void phbPage(Model model,HttpServletResponse response,Integer index,String orderBy) throws IOException {
		index=(index==null||index==0)?2:index;
		JSONObject json=new JSONObject();
		//按照总积分查询
		index=(index==null||index==0)?2:index;
		InfoCust infoCust=new InfoCust();
		infoCust.setMarry("1");
		Date date = new Date();
		Calendar cal = Calendar.getInstance();
		cal.setTime(date);
		cal.add(Calendar.MONTH, -1);
		int month= cal.get(Calendar.MONTH);
		int year=cal.get(Calendar.YEAR);
		infoCust.setYear(year+"");
		infoCust.setMonth(month+"");
		Page<InfoCust> page=new Page<InfoCust>(index, 15);
		page.setOrderBy(orderBy+" desc");
		page=infoCustService.findPartyPage(page, infoCust);
		for(InfoCust cust:page.getList()) {
			if("a.party_score".equals(orderBy)) {
				cust.setScore(cust.getPartyScore());
			}else if("a.act_score".equals(orderBy)) {
				cust.setScore(cust.getActScore());
			}else if("a.game_score".equals(orderBy)) {
				cust.setScore(cust.getGameScore());
			}
		}
		if(page.getPageCount()<index) {
			json.put("list", JsonMapper.getInstance().toJson(new ArrayList<InfoCust>()));
			json.put("size", 0);
		}else {
			json.put("list", JsonMapper.getInstance().toJson(page.getList()));
			json.put("size", page.getList().size());
		}
		response.getWriter().println(json);;
	}
	
	
	/**
	 * 获取微信accessToken值
	 * @param code
	 * @return
	 */
	 @SuppressWarnings("deprecation")
	public Map<String, String> getUserInfoAccessToken(String code) {
	        JsonObject object = null;
	        Map<String, String> data = new HashMap<String, String>();
	        try {
	            String url = String.format("https://api.weixin.qq.com/sns/oauth2/access_token?appid=%s&secret=%s&code=%s&grant_type=authorization_code",
	                                       APPID, APPSECRET, code);
	           System.out.println("request accessToken from url: {}"+url);
	            DefaultHttpClient httpClient = new DefaultHttpClient();
	            HttpGet httpGet = new HttpGet(url);
	            HttpResponse httpResponse = httpClient.execute(httpGet);
	            HttpEntity httpEntity = httpResponse.getEntity();
	            String tokens = EntityUtils.toString(httpEntity, "utf-8");
	            Gson token_gson = new Gson();
	            object = token_gson.fromJson(tokens, JsonObject.class);
	           System.out.println("request accessToken success. [result={}]"+object);
	            data.put("openid", object.get("openid").toString().replaceAll("\"", ""));
	            data.put("access_token", object.get("access_token").toString().replaceAll("\"", ""));
	        } catch (Exception ex) {
	            System.out.println("fail to request wechat access token. [error={}]"+ ex);
	        }
	        return data;
	    }
	 /**
	  * 获取微信用户信息
	  * @param accessToken
	  * @param openId
	  * @return
	  */
	 public Map<String, String> getUserInfo(String accessToken, String openId) {
	        Map<String, String> data = new HashMap();
	        String url = "https://api.weixin.qq.com/sns/userinfo?access_token=" + accessToken + "&openid=" + openId + "&lang=zh_CN";
	        System.out.println("request user info from url: {}"+url);
	        JsonObject userInfo = null;
	        try {
	            DefaultHttpClient httpClient = new DefaultHttpClient();
	            HttpGet httpGet = new HttpGet(url);
	            HttpResponse httpResponse = httpClient.execute(httpGet);
	            HttpEntity httpEntity = httpResponse.getEntity();
	            String response = EntityUtils.toString(httpEntity, "utf-8");
	            Gson token_gson = new Gson();
	            userInfo = token_gson.fromJson(response, JsonObject.class);
	            System.out.println("get userinfo success. [result={}]"+userInfo);
	            data.put("openid", userInfo.get("openid").toString().replaceAll("\"", ""));
	            data.put("nickname", userInfo.get("nickname").toString().replaceAll("\"", ""));
	            data.put("city", userInfo.get("city").toString().replaceAll("\"", ""));
	            data.put("province", userInfo.get("province").toString().replaceAll("\"", ""));
	            data.put("country", userInfo.get("country").toString().replaceAll("\"", ""));
	            data.put("headimgurl", userInfo.get("headimgurl").toString().replaceAll("\"", ""));
	        } catch (Exception ex) {
	        	 System.out.println("fail to request wechat user info. [error={}]"+ex);
	        }
	        return data;
	    }
	 
	 @RequestMapping(value = "map")
		public String map(HttpServletRequest request,Model model,Integer index) {
			
			return "dangj/map";
		 }
		
}
