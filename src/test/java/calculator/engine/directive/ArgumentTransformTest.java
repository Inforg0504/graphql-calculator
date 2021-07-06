/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to you under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package calculator.engine.directive;

import calculator.config.Config;
import calculator.config.ConfigImpl;
import calculator.engine.ExecutionEngine;
import calculator.engine.SchemaHolder;
import calculator.engine.SchemaWrapper;
import calculator.engine.script.AviatorScriptEvaluator;
import calculator.engine.script.ListContain;
import calculator.engine.script.ListMapper;
import calculator.validation.Validator;
import graphql.ExecutionInput;
import graphql.ExecutionResult;
import graphql.GraphQL;
import graphql.ParseAndValidateResult;
import graphql.schema.GraphQLSchema;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static calculator.engine.TestUtil.listsWithSameElements;

public class ArgumentTransformTest {


    private static final GraphQLSchema originalSchema = SchemaHolder.getSchema();
    private static final Config wrapperConfig = ConfigImpl.newConfig().scriptEvaluator(AviatorScriptEvaluator.getDefaultInstance()).build();
    private static final GraphQLSchema wrappedSchema = SchemaWrapper.wrap(wrapperConfig, originalSchema);
    private static final GraphQL graphQL = GraphQL.newGraphQL(wrappedSchema).instrumentation(ExecutionEngine.newInstance(wrapperConfig)).build();
    static {
        AviatorScriptEvaluator.getDefaultInstance().addFunction(new ListContain());
        AviatorScriptEvaluator.getDefaultInstance().addFunction(new ListMapper());
    }

    @Test
    public void getItemListBindingCouponIdAndFilterUnSaleItems() {
        String query = ""
                + "query( $couponId: Int){\n" +
                "    commodity{\n" +
                "        itemList(itemIds: 1)\n" +
                "        @argumentTransform(argumentName: \"itemIds\", operateType: MAP,dependencySource: \"itemIdList\",expression: \"itemIdList\")\n" +
                "        @filter(predicate: \"onSale\")\n" +
                "        {\n" +
                "           itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "            onSale\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    marketing{\n" +
                "        coupon(couponId: $couponId){\n" +
                "            bindingItemIds\n" +
                "            @fetchSource(name: \"itemIdList\")\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema, wrapperConfig);
        assert !validateResult.isFailure();

        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(Collections.singletonMap("couponId", 1L))
                .build();
        ExecutionResult executionResult = graphQL.execute(skipInput);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert listsWithSameElements(((Map<String, List>) data.get("marketing").get("coupon")).get("bindingItemIds"), Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<Map<String, Object>> itemListInfo = (List<Map<String, Object>>) data.get("commodity").get("itemList");
        assert itemListInfo.size() == 7;
        assert Objects.equals(
                itemListInfo.toString(),
                "[{itemId=1, name=item_name_1, salePrice=11, onSale=true}, " +
                        "{itemId=2, name=item_name_2, salePrice=21, onSale=true}, " +
                        "{itemId=4, name=item_name_4, salePrice=41, onSale=true}, " +
                        "{itemId=5, name=item_name_5, salePrice=51, onSale=true}, " +
                        "{itemId=7, name=item_name_7, salePrice=71, onSale=true}, " +
                        "{itemId=8, name=item_name_8, salePrice=81, onSale=true}, " +
                        "{itemId=10, name=item_name_10, salePrice=101, onSale=true}]"
        );
    }

    @Test
    public void filterItemListByBindingCouponIdAndFilterUnSaleItems() {
        String query = ""
                + "query filterItemListByBindingCouponIdAndFilterUnSaleItems ( $couponId: Int,$itemIds: [Int]) {\n" +
                "    commodity{\n" +
                "        itemList(itemIds: $itemIds)\n" +
                "        @argumentTransform(argumentName: \"itemIds\", operateType: FILTER,dependencySource: \"itemIdList\",expression: \"listContain(itemIdList,ele)\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "            onSale\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    marketing{\n" +
                "        coupon(couponId: $couponId){\n" +
                "            bindingItemIds\n" +
                "            @fetchSource(name: \"itemIdList\")\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema ,wrapperConfig);
        assert !validateResult.isFailure();

        HashMap<String, Object> variables = new LinkedHashMap<>();
        // 绑定商品id 1-10，onSale = (id%3 == 0);
        variables.put("couponId", 1L);
        variables.put("itemIds", Arrays.asList(9, 10, 11, 12));
        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphQL.execute(skipInput);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert listsWithSameElements(((Map<String, List>) data.get("marketing").get("coupon")).get("bindingItemIds"), Arrays.asList(1, 2, 3, 4, 5, 6, 7, 8, 9, 10));
        List<Map<String, Object>> itemListInfo = (List<Map<String, Object>>) data.get("commodity").get("itemList");
        assert itemListInfo.size() == 2;
        System.out.println(itemListInfo.toString());
        assert Objects.equals(
                itemListInfo.toString(),
                "[{itemId=9, name=item_name_9, salePrice=91, onSale=false}, {itemId=10, name=item_name_10, salePrice=101, onSale=true}]"
        );
    }


    @Test
    public void mapListArgument() {
        String query = ""
                + "query mapListArgument($itemIds: [Int]){ \n" +
                "    commodity{\n" +
                "        itemList(itemIds: $itemIds)\n" +
                "        @argumentTransform(argumentName: \"itemIds\",operateType: LIST_MAP,expression: \"ele*10\")\n" +
                "        {\n" +
                "            itemId\n" +
                "            name\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema,wrapperConfig);
        assert !validateResult.isFailure();

        HashMap<String, Object> variables = new LinkedHashMap<>();
        // 绑定商品id 1-10，onSale = (id%3 == 0);
        variables.put("itemIds", Arrays.asList(1, 2, 3));
        ExecutionInput input = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphQL.execute(input);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Map<String, List<Map<String, Object>>>> data = executionResult.getData();
        List<Map<String, Object>> itemListInfo = data.get("commodity").get("itemList");
        assert itemListInfo.size() == 3;
        assert Objects.equals(
                itemListInfo.toString(),
                "[{itemId=10, name=item_name_10}, {itemId=20, name=item_name_20}, {itemId=30, name=item_name_30}]"
        );
    }


    @Test
    public void calculateCouponPrice_Case01() {
        String query = "" +
                "# 查找券信息和列表商品信息，如果券绑定了商品，则返回券后价\n" +
                "query calculateCouponPrice_Case01 ($couponId: Int, $itemIds: [Int]){\n" +
                "\n" +
                "    marketing{\n" +
                "        coupon(couponId: $couponId)\n" +
                "        @fetchSource(name: \"itemCouponInfo\",sourceConvert: \"list2MapWithAssignedValue('bindingItemIds','price')\")\n" +
                "        {\n" +
                "            base\n" +
                "            price\n" +
                "            bindingItemIds\n" +
                "            desc: couponText @mapper(expression: \"'满' + base + '减' + price\")\n" +
                "        }\n" +
                "    }\n" +
                "\n" +
                "    commodity{\n" +
                "        itemList(itemIds: $itemIds){\n" +
                "            itemId\n" +
                "            name\n" +
                "            salePrice\n" +
                "            isUsedCoupon: onSale @mapper(dependencySource: \"itemCouponInfo\",expression: \"seq.get(itemCouponInfo,itemId)!=nil\")\n" +
                "            # 券后价\n" +
                "            couponPrice: salePrice @mapper(dependencySource: \"itemCouponInfo\",expression: \"salePrice - (seq.get(itemCouponInfo,itemId) == nil? 0:seq.get(itemCouponInfo,itemId)) \")\n" +
                "        }\n" +
                "    }\n" +
                "}";

        ParseAndValidateResult validateResult = Validator.validateQuery(query, wrappedSchema,wrapperConfig);
        assert !validateResult.isFailure();

        System.out.println(query);

        HashMap<String, Object> variables = new LinkedHashMap<>();
        // 绑定的商品为 [1,10]
        variables.put("couponId", 1);
        // 11和12不能使用券
        variables.put("itemIds", Arrays.asList(9,10,11,12));
        ExecutionInput skipInput = ExecutionInput
                .newExecutionInput(query)
                .variables(variables)
                .build();
        ExecutionResult executionResult = graphQL.execute(skipInput);

        assert executionResult != null;
        assert executionResult.getErrors() == null || executionResult.getErrors().isEmpty();
        Map<String, Map<String, Object>> data = executionResult.getData();
        assert ((Map<String,Object>)data.get("marketing").get("coupon")).get("desc").toString().equals("满21减13");

        List<Map<String, Object>> itemListInfo = (List<Map<String, Object>> )data.get("commodity").get("itemList");
        assert itemListInfo.size() == 4;
        System.out.println(itemListInfo);
        assert Objects.equals(itemListInfo.toString(),
                ""
                        // item with coupon
                        + "[{itemId=9, name=item_name_9, salePrice=91, isUsedCoupon=true, couponPrice=78}, "
                        + "{itemId=10, name=item_name_10, salePrice=101, isUsedCoupon=true, couponPrice=88}, "
                        // item without coupon
                        + "{itemId=11, name=item_name_11, salePrice=111, isUsedCoupon=false, couponPrice=111}, "
                        + "{itemId=12, name=item_name_12, salePrice=121, isUsedCoupon=false, couponPrice=121}]"
                );
    }

}