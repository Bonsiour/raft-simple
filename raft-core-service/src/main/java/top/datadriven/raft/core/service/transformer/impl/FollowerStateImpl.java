package top.datadriven.raft.core.service.transformer.impl;

import com.google.common.collect.Lists;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import top.datadriven.raft.config.loader.ConfigLoader;
import top.datadriven.raft.core.model.config.ConfigModel;
import top.datadriven.raft.core.model.config.RaftNodeModel;
import top.datadriven.raft.core.model.enums.ServerStateEnum;
import top.datadriven.raft.core.model.exception.ErrorCodeEnum;
import top.datadriven.raft.core.model.exception.RaftException;
import top.datadriven.raft.core.model.model.LeaderStateModel;
import top.datadriven.raft.core.model.model.RaftCoreModel;
import top.datadriven.raft.core.model.util.CommonUtil;

import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;

/**
 * @description: follower状态
 * @author: jiayancheng
 * @email: jiayancheng@foxmail.com
 * @datetime: 2020/4/15 11:36 下午
 * @version: 1.0.0
 */
@Service(value = "followerStateImpl")
public class FollowerStateImpl extends AbstractServerStateTransformer {

    @Override
    public void execute() {
        String channelFlag;
        try {
            //1.每过6~10个心跳时间获取一次心跳标志
            channelFlag = RaftCoreModel.getSingleton()
                    .getHeartbeatChannel()
                    .poll(CommonUtil.getInterval(6, 10), TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            throw new RaftException(ErrorCodeEnum.CHANNEL_ERROR, "定时获取channelFlag异常");
        }

        //2. 判断通知结果
        //如果心跳超时（结果为空），进行下一个状态；否则继续循环
        if (StringUtils.isBlank(channelFlag)) {
            RaftCoreModel.getSingleton().setServerStateEnum(ServerStateEnum.CANDIDATE);
        }

        //3.执行后续节点
        executeNext();

    }

    @Override
    public void preDo() {
        // do nothing
        Lock lock = RaftCoreModel.getLock();
        lock.lock();
        try {
            RaftCoreModel coreModel = RaftCoreModel.getSingleton();
            LeaderStateModel leaderState = coreModel.getLeaderState();

            ConfigModel config = ConfigLoader.load();
            for (RaftNodeModel node : config.getAllNodes()) {
                //对于每一个服务器，需要发送给他的下一个日志条目的索引值（初始化为领导人最后索引值加一）
                leaderState.getNextIndex().put(node.getServerId(),
                        coreModel.getPersistentState().getLastEntry().getIndex() + 1);
                leaderState.getMatchIndex().put(node.getServerId(), 0L);
            }
        } finally {
            lock.unlock();
        }

    }

    @Override
    public List<ServerStateEnum> getNextStates() {
        return Lists.newArrayList(
                ServerStateEnum.FOLLOWER,
                ServerStateEnum.CANDIDATE
        );
    }

    @Override
    public ServerStateEnum getCurrentState() {
        return ServerStateEnum.FOLLOWER;
    }

}
