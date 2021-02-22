# Criteria Resolver
A library to ease the usage of Criteria Queries with multiple and optional fields and projections for your response

# Sumário
- [Componentes](#componentes)
	- [SearchObject](#search-object)
    - [CriteriaField](#criteria-field)
    - [ProjectionFIeld](#projection-field)
    - [CriteriaResolverBaseRepository](#criteria-resolver-base-repository)
## Componentes <a name="componentes"></a>
### public abstract class SearchObject <a name="search-object"></a>
- Classe abstrata que deve ser estendida pelo objeto que representa a consulta a ser realizada pelo CriteriaResolver
```java
public abstract class SearchObject {
    @ApiModelProperty(hidden = true)
    private Map<String, JoinType> joins = new HashMap<>();
    public Map<String, JoinType> getJoins() {
        return joins;
    }

    public void createJoins() {

    }

    public SearchObject(){
        this.createJoins();
    }
}
```
- O Método **createJoins()** pode ser sobrescrito para definir os joins adicionando ao mapa joins, sendo a chave o nome da propriedade e o valor o tipo de Join
    - Exemplo de uso
```java
    @Override
    public void createJoins() {
        this.getJoins().put("entidade", JoinType.LEFT);
    }
```

### public @interface CriteriaField <a name="criteria-field"></a>
- Anotação criada para definir o campo de uma classe que estende SearchObject como um campo de consulta que vai gerar uma restrição na query.  
```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface CriteriaField {
    String fieldName() default "";
    ComparationType comparationType() default ComparationType.EQUALS;

    enum ComparationType {
        EQUALS, LIKE, GREATER_THAN, LESS_THAN, GREATER_EQUALS, LESS_EQUALS, IN, NOT_IN, DIFFERENT, STARTS_WITH, ENDS_WITH
    }
}
```
- Por padrão a consulta vai ser gerada utilizando o nome da propriedade anotada como o nome do campo pesquisado, mas pode ser alterado pela propriedade **fieldName**, 
servindo também para acessar propriedades de objetos aninhados.
- A propriedade **comparationType** define o tipo de comparação que deve ser feita na consulta, seu valor deve ser uma opção do Enum interno **ComparationType** e seu valor padrão é EQUALS
    - Exemplo de uso
```java
@Getter
@Setter
public class ProductSearchObject extends SearchObject {
    @CriteriaField(comparationType = CriteriaField.ComparationType.LIKE)
    private String name;
    @CriteriaField(comparationType = CriteriaField.ComparationType.LIKE)
    private String description;
    @CriteriaField(fieldName = "price", comparationType = CriteriaField.ComparationType.GREATER_EQUALS)
    private Double minPrice;
    @CriteriaField(fieldName = "price", comparationType = CriteriaField.ComparationType.LESS_EQUALS)
    private Double maxPrice;
    @CriteriaField(fieldName = "price")
    private Double exactPrice;
    @CriteriaField(fieldName = "category.name", comparationType = CriteriaField.ComparationType.LIKE)
    private String category;
}
```
- Apenas os campos preenchidos serão considerados na hora de gerar a consulta. Qualquer campo vazio é apenas ignorado.
### public @interface ProjectionField <a name="projection-field"></a>
- Anotação criada para potencializar o uso de DTOs no retorno das consultas geradas utilizando **CriteriaResolver**. Uma consulta genérica pode ter seus campos de retorno definidos ao se passar como parâmetro uma Classe desejada.
```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
@Inherited
public @interface ProjectionField {
    String projectionPath();
}
```
- O campo **projectionPath** define o nome da propriedade que se deseja retornada, seu valor padrão é o nome da propriedade anotada e pode ser utilizada para acessar propriedades de objetos aninhados. A anotação é opcional caso o nome da propriedade da classe de retorno seja igual ao do campo desejado.
    - Exemplo
```java
public class ProductDTO {
    @ProjectionField(projectionPath = "name")
    private String productName;
    private Double price;
    @ProjectionField(projectionPath = "category.name")
    private String categoryName;
}
```
- A classe passada como retorno deve ter um construtor com todos os argumentos.

### public abstract class CriteriaResolverBaseRepository<T> <a name="criteria-resolver-base-repository"></a>
- Repositório base que deve ser estendido para gerar as consultas com CriteriaResolver
```java
public abstract class CriteriaResolverBaseRepository<T> {
    EntityManager em;

    private Class<T> c;
    private CriteriaBuilder criteriaBuilder;
    private CriteriaQuery<T> criteria;
    private Root<T> root;
    private Map<String, Join> joinMap;

    @SuppressWarnings("unchecked")
    public CriteriaResolverBaseRepository(EntityManager entityManager){
        ParameterizedType genericSuperclass = (ParameterizedType) getClass().getGenericSuperclass();
        this.c = (Class<T>) genericSuperclass.getActualTypeArguments()[0];
        this.em = entityManager;
        joinMap = new HashMap<>();
    }

    public List<T> getResultList(SearchObject searchObject){
        return this.getQuery(searchObject).getResultList();
    }

    public T getSingleResult(SearchObject searchObject) {
        return this.getQuery(searchObject).getSingleResult();
    }

    @SuppressWarnings("unchecked")
    public <D> TypedQuery<D> getGenericQuery(SearchObject searchObject, Class<D> clazz){
        CriteriaQuery<D> genericCriteria;
        criteriaBuilder = em.getCriteriaBuilder();
        genericCriteria = criteriaBuilder.createQuery(clazz);
        root = genericCriteria.from(c);
        createJoins(searchObject);
        setProjections(genericCriteria, clazz);
        List<Predicate> predicates = getPredicates(root, searchObject);
        genericCriteria.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(genericCriteria);
    }

    public TypedQuery<T> getQuery(SearchObject searchObject){
        criteriaBuilder = em.getCriteriaBuilder();
        criteria = criteriaBuilder.createQuery(c);
        root = criteria.from(c);
        createJoins(searchObject);
        List<Predicate> predicates = getPredicates(root, searchObject);
        criteria.where(predicates.toArray(new Predicate[0]));
        return em.createQuery(criteria);
    }

    private void setProjections(CriteriaQuery genericCriteria, Class clazz) {
        List<String> projections = new ArrayList();
        for(Field field: clazz.getDeclaredFields()) {
            projections.add(getProjection(field));
        }
        List<Selection> selectionList = new ArrayList<>();
        Selection[] selectionArray;
        if(projections.size() == 0)
            return;
        for (String projection : projections) {
            selectionList.add(getPath(projection));
        }
        selectionArray = new Selection[selectionList.size()];
        genericCriteria.multiselect(selectionList.toArray(selectionArray));
    }

    private String getProjection(Field field) {
        field.setAccessible(true);
        String projectionPath = field.getName();
        ProjectionField[] annotationsByType = field.getAnnotationsByType(ProjectionField.class);
        if(annotationsByType != null && annotationsByType.length == 1) {
            projectionPath = annotationsByType[0].projectionPath();
        }
        return projectionPath;
    }

    private void createJoins(SearchObject searchObject) {
        Map<String, JoinType> joins = searchObject.getJoins();
        Set<String> keySet = joins.keySet();
        for (String key : keySet) {
            joinMap.put(key, root.join(key, joins.get(key)));
        }
    }

    private List<Predicate> getPredicates(Root<T> root, SearchObject searchObject) {
        List<Predicate> predicates = new ArrayList<>();
        for(Field field: searchObject.getClass().getDeclaredFields()) {
            field.setAccessible(true);
            String fieldName;
            CriteriaField.ComparationType comparationType;
            CriteriaField[] annotationsByType = field.getAnnotationsByType(CriteriaField.class);
            if(annotationsByType.length == 1) {
                try {
                    Object value = field.get(searchObject);
                    if(value == null)
                        continue;
                    CriteriaField criteriaField = annotationsByType[0];
                    if(criteriaField.fieldName().equals("")) {
                        fieldName = field.getName();
                    }else {
                        fieldName = criteriaField.fieldName();
                    }
                    comparationType = criteriaField.comparationType();
                    predicates.add(getPredicate(fieldName, comparationType, value));
                }catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }
        return predicates;
    }

    private Path getPath(String attributeName) {
        Path path = root;
        for (String part : attributeName.split("\\.")) {
            path = path.get(part);
        }
        return path;
    }

    @SuppressWarnings("unchecked")
    private Predicate getPredicate(String fieldName, CriteriaField.ComparationType comparationType, Object value) {
        Predicate predicate = null;
        switch(comparationType) {
            case LIKE:
                predicate = criteriaBuilder.like(getPath(fieldName), "%" +value + "%");
                break;
            case STARTS_WITH:
                predicate = criteriaBuilder.like(getPath(fieldName), value + "%");
                break;
            case ENDS_WITH:
                predicate = criteriaBuilder.like(getPath(fieldName), "%" +value);
                break;
            case GREATER_THAN:
                predicate = criteriaBuilder.greaterThan(getPath(fieldName), (Comparable) value);
                break;
            case GREATER_EQUALS:
                predicate = criteriaBuilder.greaterThanOrEqualTo(getPath(fieldName), (Comparable) value);
                break;
            case LESS_THAN:
                predicate = criteriaBuilder.lessThan(getPath(fieldName), (Comparable) value);
                break;
            case LESS_EQUALS:
                predicate = criteriaBuilder.lessThanOrEqualTo(getPath(fieldName), (Comparable) value);
                break;
            case IN:
                predicate = criteriaBuilder.in(getPath(fieldName)).value(value);
                break;
            case NOT_IN:
                predicate = criteriaBuilder.in(getPath(fieldName)).value(value).not();
                break;
            case DIFFERENT:
                predicate = criteriaBuilder.notEqual(getPath(fieldName), value);
                break;
            default:
                predicate = criteriaBuilder.equal(getPath(fieldName), value);
                break;
        }
        return predicate;
    }
}
```
- Os métodos básicos para o funcionamento das consultas básicas já estão implementados, sendo necessário apenas criar um repositório que defina o ponto inicial das consultas.
    - Exemplo
```java
@Repository
public class ProductCriteriaRepository extends CriteriaBaseRepository<Product> {
    public ProductCriteriaRepository(EntityManager entityManager) {
        super(entityManager);
    }
}
```
- O repositório já está pronto para o uso com os métodos **getQuery(SearchObject searchObject)**, **getSingleResult(SearchObject searchObject)**, **getResultList(SearchObject searchObject)** e **getGenericQuery(SearchObject searchObject, Class<D> clazz)**
    - Exemplo
```java
public List<ProductDTO> getProductByCriteria(ProductSearchObject productSearchObject){
    return productCriteriaRepository.getGenericQuery(productSearchObject, ProductDTO.class).setFirstResult(10).setMaxResults(10).getResultList();
}
```
- Esse código sendo o suficiente para realizar uma consulta paginada apenas pelos campos preenchidos do objeto de consulta e com as projeções no formato do objeto de retorno.
