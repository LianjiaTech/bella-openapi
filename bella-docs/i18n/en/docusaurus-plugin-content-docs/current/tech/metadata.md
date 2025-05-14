# Four-layer Metadata: Decoding Bella OpenAPI's Extensible Architecture Design

## Introduction: Architectural Design Challenges

When enterprises need to build a unified gateway supporting multiple AI capabilities, one of the biggest challenges is designing an architecture that is both flexible enough and easy to manage. Traditional API gateways typically adopt a simple API registration model, which struggles to handle the diverse, complex, and rapidly evolving nature of AI services. Bella OpenAPI elegantly solves this problem through its unique four-layer metadata architecture. This article will analyze its design philosophy and implementation details in depth.

## Four-layer Metadata Architecture: An Elegant Layered Design

From Bella OpenAPI's source code, we can clearly see that its core adopts a Category-Endpoint-Model-Channel four-layer structure. This layering is not arbitrary but carefully built based on the essential characteristics and management requirements of AI services.

### First Layer: Category

Category is the top-level structure of the entire metadata system, used for logical grouping of different types of AI capabilities. From the code implementation:

```java
// CategoryRepo.java
@Component
public class CategoryRepo extends StatusRepo<CategoryDB, 
CategoryRecord, String> {
 // ...
 private String generateCategoryCode(String parentCode) {
 // Hierarchical coding generation logic
 }
 
 // Support for tree structure queries
 public List<CategoryDB> 
queryAllChildrenIncludeSelfByCategoryCode(String categoryCode, 
String status) {
 // ...
 }
}
```

Core design features:
- Tree structure: Supports parent-child relationships, allowing the construction of multi-level category trees
- Coding mechanism: Automatically generates category codes, ensuring uniqueness and readability
- Flexible grouping: Allows categorization based on business requirements or technical characteristics

This layer solves the problem of "how to organize and manage various AI capabilities," allowing users to browse and select needed functions intuitively, such as voice services, text services, image services, etc.

### Second Layer: Endpoint

Endpoint represents specific API function entry points, such as chat completion, real-time speech recognition, etc. From the code implementation:

```java
// ModelRepo.java
@Component
public class ModelRepo extends StatusRepo<ModelDB, ModelRecord, 
String> {
 
 // Support for model and endpoint association
 // Model visibility control
 @Transactional
 public void updateVisibility(String modelName, String 
visibility) {
 // ...
 }
 
 // Support for precise querying of model features
 private SelectSeekStep1<Record, Long> 
constructSql(Condition.ModelCondition op) {
 // Complex queries based on JSON properties
 if (CollectionUtils.isNotEmpty(op.getFeatures())) {
 for (String feature : op.getFeatures()) {
 featuresCondition = featuresCondition.and(
 MODEL.FEATURES.like("%\"" + feature + 
"\":true%")
 );
 }
 }
 // ...
 }
}
```

Core design features:
- Multi-dimensional attributes: Uses JSON fields to store complex model attributes and features
- Visibility control: Supports multiple visibility levels such as public and private
- Association with endpoints: Implements many-to-many associations with Endpoints through the ModelEndpointRel table
- Authorization mechanism: Implements fine-grained authorization based on users or organizations through the ModelAuthorizerRel table

This layer solves the problem of "how to flexibly configure different AI models and their capability features," enabling the system to support multiple models and implement fine-grained permission control.

### Fourth Layer: Channel

Channel is the specific service provider implementation, associated with capability points/models, including vendor, protocol, and configuration information. From the code implementation:

```java
// ChannelRepo.java
@Component
public class ChannelRepo extends StatusRepo<ChannelDB, 
ChannelRecord, String> implements AutogenCodeRepo<ChannelRecord> 
{
 
 // Support for price information queries
 public Map<String, String> queryPriceInfo(List<String> 
entityCodes) {
 // ...
 }
 
 // Support for supplier lists
 public List<String> listSuppliers() {
 return 
db.selectDistinct(CHANNEL.SUPPLIER).from(CHANNEL).fetchInto(String.class);
 }
 
 // Support for multiple filter conditions
 private SelectSeekStep1<ChannelRecord, Long> 
constructSql(Condition.ChannelCondition op) {
 // Multi-dimensional conditions such as supplier, protocol, priority, target, etc.
 }
}
```

From EndpointContext.java, we can see the role of Channel in actual calls:

```java
public static void setEndpointData(String endpoint, String model, 
ChannelDB channel, Object request) {
 EndpointContext.getProcessData().setRequest(request);
 EndpointContext.getProcessData().setEndpoint(endpoint);
 EndpointContext.getProcessData().setModel(model);
EndpointContext.getProcessData().setChannelCode(channel.getChannelCode());
 
EndpointContext.getProcessData().setForwardUrl(channel.getUrl());
 
EndpointContext.getProcessData().setProtocol(channel.getProtocol());
 
EndpointContext.getProcessData().setPriceInfo(channel.getPriceInfo());
 
EndpointContext.getProcessData().setSupplier(channel.getSupplier());
}
```

Core design features:
- Multi-entity support: Provides two types of entities—capability points/models—offering flexible binding methods, allowing requests either through models or directly through capability points without specifying models
- Multi-vendor support: Can configure different AI service providers
- Protocol encapsulation: Abstracts API differences between different service providers, providing a unified interface
- Price management: Includes price information, supporting billing functionality
- Routing control: Implements intelligent routing through priority and target settings

This layer solves the problem of "how to flexibly connect different service providers," allowing the system to transparently manage and switch between multiple underlying services.

## Collaborative Mechanism of the Four-layer Architecture

These four layers collaborate in the following manner:
- Category selection: Users select needed Endpoints based on classification
- Request entry: Users initiate requests through Endpoints
- Model selection: When initiating a request, if a model needs to be specified, an appropriate Model can be selected under the Endpoint
- Channel routing: Select an appropriate Channel based on routing strategies
- Request forwarding: Forward the request to the target service and handle responses

## Extensibility Analysis: Advantages of the Four-layer Architecture

Through in-depth analysis of Bella OpenAPI's four-layer metadata architecture, we can discover its significant advantages in terms of extensibility:

### 1. Horizontal Scaling Capability

Bella OpenAPI's four-layer architecture enables the system to scale horizontally in various dimensions:
- Adding categories: New AI capability categories can be easily added without modifying existing structures
- Adding endpoints: New API capabilities can be added to existing categories, such as new voice processing functions
- Adding models: Support for new AI models can be added, such as integrating newly released large language models
- Adding channels: New service providers can be connected, such as integrating new cloud service providers

### 2. Vertical Deepening Capability

In addition to horizontal scaling, the four-layer architecture also supports vertical deepening at each layer:
- Category hierarchy: Supports multi-level category trees, allowing classification refinement as needed
- Endpoint capabilities: New parameters and functions can be added to endpoints
- Model features: New attributes and feature flags can be added to models
- Channel configuration: Routing strategies and configuration parameters for channels can be refined

This vertical deepening capability allows the system to adapt to various complex business requirements, not just simple API forwarding.

### 3. Loose Coupling Design

An important feature of the four-layer architecture is that the layers establish many-to-many relationships through association tables (such as EndpointCategoryRel, ModelEndpointRel, etc.), achieving loose coupling:
- One category can contain multiple endpoints
- One endpoint can support multiple models, or provide no models
- One endpoint/model can have multiple channel implementations

This loose coupling design allows the system to flexibly respond to changes, for example:
- When the underlying model for an endpoint needs to be changed, only the association relationship needs to be modified
- When a service provider has issues, it's easy to quickly switch to a backup channel
- When business requirements change, category organization can be adjusted without affecting the underlying implementation
- Supports both model capability points (such as chat completions) and non-model capability points (such as Rag)

### 4. Metadata-driven Extensibility

Bella OpenAPI's architecture is essentially "metadata-driven," meaning:
- Most extensions can be completed by configuring metadata, without modifying code
- System behavior is defined by metadata, making the extension process more controllable
- Metadata can be managed through a UI interface, making extension more convenient
- This metadata-driven approach enables the system to adapt to the rapidly changing AI technology environment while maintaining stability

## Practical Application: Production Environment Validation of the Four-layer Architecture

Bella OpenAPI has been validated on a large scale in Beike's production environment, with daily call volumes reaching 150 million. This scale proves the effectiveness of the four-layer metadata architecture in practical applications:
- Managing various AI capability points: Through the organization of categories and endpoints
- Integrating various AI models: From open-source models to commercial services
- Connecting multiple service providers: Including mainstream cloud services and professional AI service providers
- Supporting hundreds of millions of request dispatches: Through multi-level routing and scheduling mechanisms

This architecture has been validated not only in terms of functional richness but also in performance and reliability.

## Best Practices for Implementing the Four-layer Architecture

Based on the code analysis of Bella OpenAPI, we can summarize the following best practices for implementing a four-layer metadata architecture:

### 1. Coding Standards and Naming Conventions

Ensure clear coding rules for metadata at each layer:
- Category: Hierarchical coding, such as "0001-0002"
- Endpoint: Using the "ep-" prefix and auto-incrementing ID
- Model: Using the model name as a unique identifier
- Channel: Using the "ch-" prefix and auto-incrementing ID

### 2. Relationship Management Strategy

Use relationship tables to manage associations between different layers:
- Use intermediate tables to store many-to-many relationships
- Add additional attributes (such as weight, priority) in relationship tables
- Support methods for batch operations on relationships

### 3. Status Management Mechanism

All metadata should have unified status management:
- Use common status fields (active/inactive)
- Implement soft deletion rather than physical deletion
- Provide audit logs for status changes

### 4. Extensibility Design

Reserve sufficient extension points:
- Use JSON fields to store variable attributes
- Design a general feature flagging mechanism
- Support custom metadata attributes

## Conclusion: The Metadata-driven Future

Bella OpenAPI's four-layer metadata architecture not only addresses the challenges currently facing AI gateways but also lays a solid foundation for future extensions. As AI technology develops, we can foresee:
- More types of AI capabilities will be integrated into this architecture
- More complex routing strategies will be implemented based on this architecture
- More intelligent metadata management will make the system more adaptive

For any team planning to build an enterprise-level AI capability platform, Bella OpenAPI's four-layer metadata architecture provides a battle-tested reference model. By adopting this architecture, enterprises can build AI capability gateways that are both flexible and controllable, providing powerful support for business innovation.

If you are interested in Bella OpenAPI's four-layer metadata architecture, you are welcome to visit the GitHub repository to study its implementation details in depth, or experience the power of this architecture firsthand through the online demo version.