package com.hmdp.service.impl;

import cn.hutool.core.util.IdUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.Result;
import com.hmdp.entity.SeckillVoucher;
import com.hmdp.entity.VoucherOrder;
import com.hmdp.mapper.VoucherOrderMapper;
import com.hmdp.service.ISeckillVoucherService;
import com.hmdp.service.IVoucherOrderService;
import com.hmdp.utils.UserHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import java.time.LocalDateTime;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */
@Service
public class VoucherOrderServiceImpl extends ServiceImpl<VoucherOrderMapper, VoucherOrder> implements IVoucherOrderService {

    @Resource
    ISeckillVoucherService seckillVoucherService;

    @Override
    @Transactional
    public Result seckillVoucher(Long voucherId) {
        // 1. 查看当前秒杀券
        SeckillVoucher seckillVoucher = seckillVoucherService.getById(voucherId);
        if (seckillVoucher == null) {
            return Result.fail("秒杀券不存在");
        }
        LocalDateTime beginTime = seckillVoucher.getBeginTime();
        LocalDateTime endTime = seckillVoucher.getEndTime();

        // 2. 校验时间 库存 和 用户是否已经购买
        LocalDateTime now = LocalDateTime.now();
        if (now.isAfter(endTime)) {
            return Result.fail("秒杀已经结束");
        }
        if (now.isBefore(beginTime)) {
            return Result.fail("秒杀还未开始");
        }

        Integer stock = seckillVoucher.getStock();
        if (stock < 1) {
            return Result.fail("该券已经被抢光");
        }

        Long userId = UserHolder.getUser().getId();
        boolean isBuy = query().eq("user_id", userId).eq("voucher_id", voucherId).exists();
        if (isBuy) {
            return Result.fail("你已下单过该秒杀券");
        }


        // 3. 扣除库存 乐观锁
        boolean success = seckillVoucherService.update()
                .setSql("stock = stock - 1")
                .eq("voucher_id", voucherId)
                .gt("stock", 0)
                .update();
        if (!success) {
            return Result.fail("抢购失败，库存不足");
        }

        // 4. 创建订单
        VoucherOrder order = new VoucherOrder();
        order.setId(IdUtil.getSnowflake(1, 1).nextId());
        order.setUserId(userId);
        order.setVoucherId(voucherId);
        order.setStatus(1);

        save(order);

        return Result.ok(order.getId());
    }
}
