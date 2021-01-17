package calculator.validate;

import graphql.analysis.QueryVisitorFieldEnvironment;
import graphql.language.Argument;
import graphql.language.Directive;
import graphql.util.TraverserContext;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static calculator.CommonTools.getAliasOrName;
import static calculator.CommonTools.getArgumentFromDirective;
import static java.lang.String.format;
import static java.util.stream.Collectors.toList;
import static java.util.stream.Collectors.toSet;


/**
 * link使用的node必须存在； -
 * link指向的参数必须存在； -
 * link不能指向同一个参数；-
 * <p>
 * todo
 * node和参数类型必须兼容；
 * 定义的node是否被使用了；
 * 不能有环、还是dag；
 */
public class LinkValidator extends QueryValidationVisitor {

    private Map<String, String> nodeNameMap;

    // 在link中使用的node
    private Set<String> usedNodeName;

    // todo 是否需要考虑并发
    public LinkValidator() {
        super();
        usedNodeName = new LinkedHashSet<>();
    }

    public static LinkValidator newInstance() {
        return new LinkValidator();
    }

    public void setNodeNameMap(Map<String, String> nodeNameMap) {
        this.nodeNameMap = nodeNameMap;
    }

    public Set<String> getUsedNodeName() {
        return usedNodeName;
    }

    @Override
    public void visitField(QueryVisitorFieldEnvironment environment) {
        // 不是进入该节点则返回
        if (environment.getTraverserContext().getPhase() != TraverserContext.Phase.ENTER) {
            return;
        }


        List<Directive> linkDirList = environment.getField().getDirectives().stream()
                .filter(dir -> Objects.equals("link", dir.getName())).collect(toList());
        if (!linkDirList.isEmpty()) {
            String aliasOrName = getAliasOrName(environment.getField());
            Set<String> argumentsName = environment.getField().getArguments().stream().map(Argument::getName).collect(toSet());

            Map<String, String> argByNodeName = new HashMap<>();
            for (Directive linkDir : linkDirList) {
                // argument 必须定义在查询语句中
                String argumentName = getArgumentFromDirective(linkDir, "argument");
                if (!argumentsName.contains(argumentName)) {
                    String errorMsg = format("'%s' do not defined on '%s'@%s.",
                            argumentName,
                            aliasOrName,
                            environment.getField().getSourceLocation()
                    );
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                }


                // node必须存在
                String nodeName =getArgumentFromDirective(linkDir, "node");
                if (!nodeNameMap.containsKey(nodeName)) {
                    String errorMsg = format("the node '%s' used by '%s'@%s do not exist.", nodeName, aliasOrName, environment.getField().getSourceLocation());
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                }

                // 两个node不能同一个参数
                if (argByNodeName.containsKey(argumentName)) {
                    String errorMsg = format("node must not linked to the same argument: '%s', @link defined on '%s'@%s is invalid.",
                            argumentName,
                            aliasOrName,
                            environment.getField().getSourceLocation()
                    );
                    addValidError(linkDir.getSourceLocation(), errorMsg);
                    continue;
                } else {
                    argByNodeName.put(argumentName, nodeName);
                }
            }


        }
    }
}
