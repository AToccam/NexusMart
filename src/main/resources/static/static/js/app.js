const API_BASE = '';

const currentUserId = sessionStorage.getItem('userId');
const currentNickname = sessionStorage.getItem('nickname');
if (!currentUserId) {
    window.location.href = 'login.html';
}
document.getElementById('userGreeting').textContent =
    '👤 ' + (currentNickname || '') + '（ID: ' + currentUserId + '）';

function doLogout() {
    sessionStorage.clear();
    window.location.href = 'login.html';
}

function getUserId() {
    return currentUserId;
}

async function loadGoods() {
    const container = document.getElementById('goodsList');
    try {
        const resp = await fetch(API_BASE + '/api/goods/seckill/list');
        const result = await resp.json();
        if (result.code !== 200 || !result.data || result.data.length === 0) {
            container.innerHTML = '<p class="empty-tip">暂无正在进行的秒杀商品</p>';
            return;
        }
        container.innerHTML = result.data.map(g => `
            <div class="goods-card">
                <div class="goods-info">
                    <h3>${escapeHtml(g.goodsName)}</h3>
                    <p>
                        <span class="price-original">¥${g.goodsPrice}</span>
                        <span class="price-seckill">¥${g.seckillPrice}</span>
                    </p>
                    <p class="stock ${g.stockCount <= 0 ? 'empty' : ''}">
                        剩余库存：${g.stockCount}
                    </p>
                </div>
                <button class="btn-seckill" onclick="doSeckill(${g.seckillId})"
                        ${g.stockCount <= 0 ? 'disabled' : ''}>
                    ${g.stockCount <= 0 ? '已售罄' : '立即抢购'}
                </button>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = '<p class="empty-tip">加载失败，请检查后端服务是否启动</p>';
    }
}

async function doSeckill(seckillId) {
    const userId = getUserId();
    const box = document.getElementById('resultBox');
    box.className = 'result-box';
    box.style.display = 'none';

    try {
        const resp = await fetch(API_BASE + '/api/seckill/do', {
            method: 'POST',
            headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
            body: `userId=${encodeURIComponent(userId)}&seckillId=${encodeURIComponent(seckillId)}`
        });
        const result = await resp.json();
        if (result.code === 200) {
            box.className = 'result-box success';
            box.innerHTML = `⏳ 已进入排队，业务订单号：${result.data.orderNo}，请求ID：${escapeHtml(result.data.requestId)}，请稍后在订单列表查看结果`;
            showToast('请求已入队，订单号 #' + result.data.orderNo, 'success');
            loadGoods();
        } else {
            box.className = 'result-box error';
            box.textContent = '❌ ' + result.msg;
            showToast(result.msg, 'error');
        }
    } catch (e) {
        box.className = 'result-box error';
        box.textContent = '❌ 请求失败：' + e.message;
    }
}

function escapeHtml(str) {
    const div = document.createElement('div');
    div.appendChild(document.createTextNode(str || ''));
    return div.innerHTML;
}

function switchTab(tab) {
    document.getElementById('tabGoods').className = tab === 'goods' ? 'active' : '';
    document.getElementById('tabOrders').className = tab === 'orders' ? 'active' : '';
    document.getElementById('goodsPanel').style.display = tab === 'goods' ? '' : 'none';
    document.getElementById('ordersPanel').style.display = tab === 'orders' ? '' : 'none';
    if (tab === 'orders') loadOrders();
}

async function loadOrders() {
    const userId = getUserId();
    const container = document.getElementById('ordersList');
    container.innerHTML = '<p class="empty-tip">加载中…</p>';
    try {
        const resp = await fetch(API_BASE + '/api/seckill/orders?userId=' + encodeURIComponent(userId));
        const result = await resp.json();
        if (result.code !== 200 || !result.data || result.data.length === 0) {
            container.innerHTML = '<p class="empty-tip">暂无订单</p>';
            return;
        }
        const statusMap = { 0: '排队中', 1: '下单成功', 2: '下单失败', 3: '已支付', 4: '支付失败' };
        container.innerHTML = result.data.map(o => `
            <div class="order-card">
                <div class="order-info">
                    <span class="order-id">订单 #${o.id}</span>
                    <span>商品：${escapeHtml(o.goodsName)}</span>
                    <span>金额：¥${o.orderPrice}</span>
                    <span>时间：${o.createTime ? o.createTime.replace('T', ' ') : '-'}</span>
                </div>
                <span class="status-tag status-${o.status}">${statusMap[o.status] || '未知'}</span>
            </div>
        `).join('');
    } catch (e) {
        container.innerHTML = '<p class="empty-tip">加载失败</p>';
    }
}

function showToast(msg, type) {
    const el = document.createElement('div');
    el.className = 'toast ' + type;
    el.textContent = msg;
    document.body.appendChild(el);
    setTimeout(() => { el.classList.add('hide'); }, 2500);
    setTimeout(() => { el.remove(); }, 3000);
}

loadGoods();
