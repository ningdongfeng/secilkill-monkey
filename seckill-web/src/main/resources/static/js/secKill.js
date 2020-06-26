//js函数式开发
// function f1() {
//
// }
// function f2() {
//
// }
//2.js的模块化编程 (封装的思想) ---》比作为对象方便理解
var secKillModule ={
    //属性
    contextPath:"",
    //字段
    url:{
        randomURL: function () {
            return secKillModule.contextPath+"/seckill/random/";
        },
        seckillURL:function () {
            return secKillModule.contextPath+"/seckill/product/";
        },
        resultURL:function () {
            return secKillModule.contextPath+"/seckill/resoult/";
        }
    },

    //方法
    func :{
        initDetail:function (productId,nowTime,startTime,endTime) {

            //当前时间<开始时间			 距离秒杀还有xxxx
            if(nowTime<startTime){
                //秒杀还没有开始
                //使用jquery的倒计时插件实现倒计时
                /* + 1000 防止时间偏移 这个没有太多意义，因为我们并不知道客户端和服务器的时间偏移
                这个插件简单了解，实际项目不会以客户端时间作为倒计时的，所以我们在服务器端还需要验证*/
                var killTime = new Date(startTime + 1000);
                $("#secKillTip").countdown(killTime, function (event) {
                    //时间格式
                    var format = event.strftime('距秒杀开始还有: %D天 %H时 %M分 %S秒');
                    $("#secKillTip").html("<span style='color:red;'>"+format+"</span>");
                }).on('finish.countdown', function () {
                    //倒计时结束后回调事件，已经开始秒杀，用户可以进行秒杀了，有两种方式：
                    //1、刷新当前页面
                    location.reload();
                    //或者2、调用秒杀开始的函数
                });

            }else if(nowTime>endTime){
                //当前时间>结束时间			秒杀结束
                $("#secKillTip").html("<span style='color: red;'>爽了吧 让你早点来你不来 秒杀结束啦</span>");

            }else {
                //开始时间<当前时间<结束时间	秒杀开始       开始秒杀的按钮可以供用户点击
                //$("#secKillTip").html("<button type='button' id='secKillBut'>立即秒杀</button>");
                secKillModule.func.prepareSecKill(productId)
            }
        },
        //秒杀开始前的准备
        prepareSecKill:function (productId) {
            //1.在后台验证秒杀是否真的开始  防止程序员跳过页面的验证，直接通过脚本代码去秒杀
           // 2.随机数--返回一个唯一的标识码给前端 才能进行点击秒杀 --秒杀接口的暴露
           $.ajax({
               url:secKillModule.url.randomURL()+productId,
               type:"post",
               dataType:"json",
               success:function (ret2FrontObjet) {
                   var message = ret2FrontObjet.message;
                   //本次是OK
                   if(message=="OK"){
                       var frontRandom=ret2FrontObjet.data;
                       if(frontRandom){ //字符串为空返回false，不为空为true！
                           //显示立即秒杀的按钮
                           $("#secKillTip").html("<button type='button' id='secKillBut'>立即秒杀</button>");
                           $("#secKillBut").click(function () {
                               //当点击秒杀后隐藏该按钮，防止重复提交
                               $("#secKillBut").attr("disabled",true);
                               //真正执行秒杀的函数
                               secKillModule.func.executeSecKill(frontRandom,productId);
                           })
                       }
                   }else {
                       $("#secKillTip").html("<span style='color: red;'>"+message+"</span>");
                   }
               }
           });
        },
        executeSecKill:function (frontRandom,productId) {
            $.ajax({
                url:secKillModule.url.seckillURL()+frontRandom+"/"+productId,
                type:"post",
                dataType:"json",
                success:function (data) {
                    if (data.message == "OK") {
                        $("#secKillTip").html("<span style='color: greenyellow;'>"+'订单生成成功，正在进行处理...'+"</span>");
                        //发送异步请求，获取最终秒杀结果
                        window.setInterval(function () {
                            secKillModule.func.queryResoult(productId);
                        },3000);
                    }else {
                        $("#secKillTip").html("<span style='color: red;'>"+data.message+"</span>");
                    }
                }
            });
        },
        //查询最终秒杀结果
        queryResoult:function (id) {
            $.ajax({
                url: seckillObj.url.resultURL() +id,
                type:"post",
                dataType:"json",
                success:function (rtnMessage) {
                    if(rtnMessage.errorCode == 1){
                        //秒杀成功
                        $("#seckillTip").html("<span style='color:blue;'>"+ rtnMessage.errorMessage +"</span>");
                        //终止轮询
                        window.clearInterval(seckillObj.timeFlag);
                    }else if(rtnMessage.errorCode == 0){
                        //秒杀失败
                        $("#seckillTip").html("<span style='color:blue;'>"+ rtnMessage.errorMessage +"</span>");
                        //终止轮询
                        window.clearInterval(seckillObj.timeFlag);
                    }else{
                        //3秒后，依然没有查询到结果，那么需要3秒后，继续发送请求获取秒杀结果，我们这里不需要做什么
                    }
                }
            });
        }
    }
}