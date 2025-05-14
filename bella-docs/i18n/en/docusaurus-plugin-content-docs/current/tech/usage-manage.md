# Billing and Quotas: Bella OpenAPI's Resource Management Mechanism

## Introduction: Fine-grained Management of AI Capabilities

In enterprise-level AI application scenarios, effectively managing API resources, controlling costs, and implementing reasonable billing are important challenges facing technical teams. Bella OpenAPI provides a complete solution through its carefully designed resource management mechanism, achieving full-process management from key hierarchy, quota control to usage statistics. This article will deeply analyze Bella OpenAPI's resource management architecture, revealing how it ensures the controllability and measurability of resource usage while opening up AI capabilities.

## Design Rationale of API Key Hierarchical Structure

Bella OpenAPI has designed an ingenious API Key hierarchical structure, providing a flexible and powerful foundation framework for enterprise resource management.

### 1. Parent-Child Key Relationship

By analyzing the source code of ApikeyService, we can see that the system implements a strict parent-child key relationship:

```java
@Transactional
public String createByParentCode(ApikeyCreateOp op) {
 ApikeyInfo apikey = EndpointContext.getApikey();
 // Verify if the current user has permission to create a child key
 if(!apikey.getCode().equals(op.getParentCode())) {
 throw new ChannelException.AuthorizationException("No operation permission");
 }
 // Verify that the child key quota does not exceed the parent key
 Assert.isTrue(op.getMonthQuota() == null || 
op.getMonthQuota().doubleValue() <= 
apikey.getMonthQuota().doubleValue(), "Quota exceeds the maximum quota of the ak");
 // Verify security level constraints
 Assert.isTrue(op.getSafetyLevel() <= apikey.getSafetyLevel(), 
"Security level exceeds the highest level of the ak");
 
 // Create child key...
}
```

This design allows users to:

- Implement multi-level resource allocation, from company to department to team or individual
- Control the upper limit of resources allocated to each unit
- Track resource usage by various organizational levels

### 2. Quota Inheritance and Constraints

The monthly quota of child keys is strictly constrained by parent keys, ensuring the integrity of hierarchical management:

This design ensures the rationality of resource allocation, preventing a subordinate unit from occupying too many resources and affecting overall business operations.

### 3. Resource Isolation and Sharing

Through parent-child key relationships, the system cleverly balances the needs of resource isolation and sharing:

- Resource isolation: Each child key has independent quotas and usage statistics, ensuring clear business boundaries
- Resource sharing: Parent keys can flexibly adjust child key quotas, achieving dynamic resource allocation
- Total control: The total usage of all child keys is constrained by the parent key's total quota, ensuring that the overall enterprise cost is controllable

## Implementation of Quota Control and Billing System

Bella OpenAPI has implemented a fine-grained quota control and billing system, which is an important guarantee for enterprise-level AI applications.

### 1. Monthly Quota Management

The system implements strict quota control through MonthQuotaInterceptor:

```java
// ApikeyService.java
@Value("${apikey.basic.monthQuota:200}")
private int basicMonthQuota;
// Set default quota when creating a basic key
db.setMonthQuota(op.getMonthQuota() == null ? 
BigDecimal.valueOf(basicMonthQuota) : op.getMonthQuota());
// Verify quota constraints when creating a child key
Assert.isTrue(op.getMonthQuota() == null || 
op.getMonthQuota().doubleValue() <= 
apikey.getMonthQuota().doubleValue(), "Quota exceeds the maximum quota of the ak");
```

```java
@Component
public class MonthQuotaInterceptor extends 
HandlerInterceptorAdapter {
 @Autowired
 private ApikeyService apikeyService;
 
 @Override
 public boolean preHandle(HttpServletRequest request, 
HttpServletResponse response, Object handler) {
 // Skip checking for asynchronous requests
 if 
(Boolean.TRUE.equals(request.getAttribute(ASYNC_REQUEST_MARKER))) 
{
 return true;
 }
 
 ApikeyInfo apikey = EndpointContext.getApikey();
 // Non-child ak or child ak with specified quota
 if(apikey.getParentInfo() == null || 
apikey.getMonthQuota().doubleValue() > 0) {
 BigDecimal cost = 
apikeyService.loadCost(apikey.getCode(), 
DateTimeUtils.getCurrentMonth());
 double costVal = cost.doubleValue() / 100.0;
 if(apikey.getMonthQuota().doubleValue() <= costVal) {
 String msg = "Monthly quota limit reached, limit:" + 
apikey.getMonthQuota() + ", cost:" + costVal;
 throw new 
ChannelException.RateLimitException(msg);
 }
 }
 
 // Parent ak's total quota cannot be exceeded
 if(apikey.getParentInfo() != null) {
 BigDecimal quota = 
apikey.getParentInfo().getMonthQuota();
 BigDecimal cost = 
apikeyService.loadCost(apikey.getParentCode(), 
DateTimeUtils.getCurrentMonth());
 double costVal = cost.doubleValue() / 100.0;
 if(quota.doubleValue() <= costVal) {
 String msg = "Main ak's total quota limit reached, limit:" + quota
```

This design implements dual quota assurance:

- Individual key quota control, preventing excessive use of resources at a single point
- Parent key total control, ensuring overall cost controllability

### 2. Efficient Billing Record System

The system implements efficient billing records through ApikeyCostRepo:

```java
@Transactional
public void insert(String akCode, String month) {
 ApikeyMonthCostRecord rec = APIKEY_MONTH_COST.newRecord();
 rec.setAkCode(akCode);
 rec.setMonth(month);
 rec.setAmount(BigDecimal.ZERO);
 db.insertInto(APIKEY_MONTH_COST).set(rec)
 .onDuplicateKeyIgnore()
 .execute();
}
@Transactional
public void increment(String akCode, String month, BigDecimal 
cost) {
 db.update(APIKEY_MONTH_COST)
 .set(APIKEY_MONTH_COST.AMOUNT, 
APIKEY_MONTH_COST.AMOUNT.add(cost))
 .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
 .and(APIKEY_MONTH_COST.MONTH.eq(month))
 .execute();
}
```

This design has the following advantages:

- Atomic operations: Using database transactions to ensure billing data consistency
- Idempotent design: Avoiding duplicate records to ensure billing accuracy
- Incremental recording: Using incremental updates rather than complete replacement to improve concurrent performance

### 3. Multi-layer Cache Optimization

To ensure performance in high-concurrency environments, the system implements a multi-layer caching strategy:

```java
@Cached(name = "apikey:cost:month:", key = "#akCode + ':' + 
#month", expire = 31 * 24 * 3600,
 condition = 
"T(com.ke.bella.openapi.utils.DateTimeUtils).isCurrentMonth(#month)")
@CachePenetrationProtect(timeout = 5)
public BigDecimal loadCost(String akCode, String month) {
 BigDecimal amount = apikeyCostRepo.queryCost(akCode, month);
 return amount == null ? BigDecimal.ZERO : amount;
}
```

This design greatly improves system performance:

- Conditional caching: Using cache for current month data, directly querying historical data
- Cache penetration protection: Preventing impact on the database during high concurrency
- Long-term caching: 31-day cache time ensures efficient access to monthly data

## Monitoring and Statistics of Resource Usage

Bella OpenAPI provides comprehensive resource monitoring and statistical functions, providing data support for enterprise management decisions.

### 1. Real-time Usage Queries

The system supports real-time queries of current usage, allowing users to understand resource consumption:

This allows users to:

- Monitor resource usage in real time
- Adjust resource allocation strategies promptly
- Prevent service interruptions caused by quota overruns

### 2. Historical Billing Queries

The system supports querying historical billing records, facilitating the analysis of long-term usage trends:

```java
public BigDecimal queryCost(String akCode, String month) {
 return db.select(APIKEY_MONTH_COST.AMOUNT)
 .from(APIKEY_MONTH_COST)
 .where(APIKEY_MONTH_COST.AK_CODE.eq(akCode))
 .and(APIKEY_MONTH_COST.MONTH.eq(month))
 .fetchOneInto(BigDecimal.class);
}
```

### 3. Differentiated Billing Models

Bella OpenAPI's resource recording mechanism supports implementing differentiated billing models:

- Internal cost center: Allocating quotas to different departments for internal cost accounting
- External billing mode: Creating independent keys for external customers, charging based on actual usage
- Hybrid mode: Fixed quotas for core business, pay-as-you-go for peripheral business

Implementation suggestions:

1. Set different resource prices based on cost differences of different AI models
2. Consider differentiated pricing for peak and off-peak periods
3. Provide resource package discounts for long-term stable customers

### 4. Warning and Automatic Control

Based on Bella OpenAPI's quota control mechanism, enterprises can implement warning and automatic control strategies:

- Threshold warning: Issue warnings when usage reaches 80% of the quota
- Automatic expansion: Automatically increase temporary quotas for critical business when thresholds are reached
- Intelligent degradation: Automatically degrade non-critical requests to lower-cost services when resources are tight

Implementation suggestions:

1. Establish a multi-level warning mechanism, from reminders to severe warnings
2. Preset resource expansion approval processes to ensure quick response
3. Develop business priority strategies for resource-constrained situations

## Conclusion: The Art of Resource Management

Bella OpenAPI's billing and quota system demonstrates profound insights into enterprise-level resource management: resource management is not just a technical issue, but the art of balancing control and flexibility. Through carefully designed hierarchical key structures, rigorous quota control mechanisms, efficient billing record systems, and comprehensive monitoring and statistical functions, Bella OpenAPI provides enterprises with an all-round resource management solution.

In today's rapidly developing AI technology, Bella OpenAPI's resource management mechanism enables enterprises to achieve precise control of resources and effective management of costs while opening up AI capabilities. Whether it's resource allocation within large enterprises or commercial operations of AI service providers, Bella OpenAPI provides battle-tested solutions.

When using Bella OpenAPI, you should fully utilize its resource management features, combine them with your business characteristics, and develop reasonable resource allocation strategies and monitoring mechanisms to achieve sustainable application and development of AI capabilities.

If you are interested in Bella OpenAPI's resource management mechanism, you are welcome to visit the GitHub repository to study its implementation details in depth, or experience the power of this system firsthand through the online demo version.