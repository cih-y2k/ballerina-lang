// Copyright (c) 2018 WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
//
// WSO2 Inc. licenses this file to you under the Apache License,
// Version 2.0 (the "License"); you may not use this file except
// in compliance with the License.
// You may obtain a copy of the License at
//
// http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing,
// software distributed under the License is distributed on an
// "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
// KIND, either express or implied.  See the License for the
// specific language governing permissions and limitations
// under the License.

type Person record {
    int id;
    int age = -1;
    float salary;
    string name;
    boolean married;
};

type Order record {
    int personId;
    int orderId;
    string items;
    float amount;
};

type OrderDetails record {
    int orderId;
    string personName;
    string items;
    float amount;
};

type Student record {
    string name;
    int index;
    int age = -1;
};

function testSimpleSelectAll() returns (int) {

    table<Person> personTable = table{};
    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    table<Person> personTableCopy = from personTable
    select *;
    while (personTableCopy.hasNext()) {
        var rs = personTableCopy.getNext();
        recordCount = recordCount + 1;
    }

    return recordCount;
}

function testSimpleSelectAllWithLimit() returns (int) {

    table<Person> personTable = table{};
    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    table<Person> personTableCopy = from personTable select * limit 2;
    while (personTableCopy.hasNext()) {
        var rs = personTableCopy.getNext();
        recordCount = recordCount + 1;
    }

    return recordCount;
}

function testSimpleSelectFewFields() returns (int) {

    table<Person> personTable = table{};
    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    table<Student> studentTable = from personTable
    select name, id as index, age;
    while (studentTable.hasNext()) {
        var rs = studentTable.getNext();
        recordCount = recordCount + 1;
    }

    return recordCount;
}

function testSimpleSelectWithJoin() returns (int) {

    table<Person> personTable = table{};
    table<Order> orderTable = table{};

    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    Order o1 = { personId: 1, orderId: 1234, items: "pen, book, eraser", amount: 34.75 };
    Order o2 = { personId: 1, orderId: 2314, items: "dhal, rice, carrot", amount: 14.75 };
    Order o3 = { personId: 2, orderId: 5643, items: "Macbook Pro", amount: 2334.75 };
    Order o4 = { personId: 3, orderId: 8765, items: "Tshirt", amount: 20.75 };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    _ = orderTable.add(o1);
    _ = orderTable.add(o2);
    _ = orderTable.add(o3);
    _ = orderTable.add(o4);

    table<OrderDetails> orderDetailsTable = from personTable as tempPersonTable join orderTable as tempOrderTable on
    tempPersonTable.id == tempOrderTable.personId select tempOrderTable.orderId as orderId, tempPersonTable.name as
    personName, tempOrderTable.items as items, tempOrderTable.amount as amount;

    while (orderDetailsTable.hasNext()) {
        var rs = orderDetailsTable.getNext();
        recordCount = recordCount + 1;
    }

    return recordCount;
}

function testSelectWithJoinAndWhere() returns (int) {

    table<Person> personTable = table{};
    table<Order> orderTable = table{};

    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    Order o1 = { personId: 1, orderId: 1234, items: "pen, book, eraser", amount: 34.75 };
    Order o2 = { personId: 1, orderId: 2314, items: "dhal, rice, carrot", amount: 14.75 };
    Order o3 = { personId: 2, orderId: 5643, items: "Macbook Pro", amount: 2334.75 };
    Order o4 = { personId: 3, orderId: 8765, items: "Tshirt", amount: 20.75 };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    _ = orderTable.add(o1);
    _ = orderTable.add(o2);
    _ = orderTable.add(o3);
    _ = orderTable.add(o4);

    table<OrderDetails> orderDetailsTable = from personTable where name != "jane" as tempPersonTable join orderTable
    where personId != 3 as tempOrderTable on tempPersonTable.id == tempOrderTable.personId select
    tempOrderTable.orderId as orderId, tempPersonTable.name as personName, tempOrderTable.items as items,
    tempOrderTable.amount as amount;

    while (orderDetailsTable.hasNext()) {
        var rs = orderDetailsTable.getNext();
        recordCount = recordCount + 1;
    }

    return recordCount;
}

function testSelectWithJoinAndWhereWithGroupBy() returns (int) {

    table<Person> personTable = table{};
    table<Order> orderTable = table{};

    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    Order o1 = { personId: 1, orderId: 1234, items: "pen, book, eraser", amount: 34.75 };
    Order o2 = { personId: 1, orderId: 2314, items: "dhal, rice, carrot", amount: 14.75 };
    Order o3 = { personId: 2, orderId: 5643, items: "Macbook Pro", amount: 2334.75 };
    Order o4 = { personId: 3, orderId: 8765, items: "Tshirt", amount: 20.75 };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    _ = orderTable.add(o1);
    _ = orderTable.add(o2);
    _ = orderTable.add(o3);
    _ = orderTable.add(o4);

    table<OrderDetails> orderDetailsTable = from personTable as tempPersonTable join orderTable as tempOrderTable on
    tempPersonTable.id == tempOrderTable.personId
    select tempOrderTable.orderId as orderId, tempPersonTable.name as personName, tempOrderTable.items as
    items, tempOrderTable.amount as amount group by tempOrderTable.orderId, tempPersonTable.name,
    tempOrderTable.items, tempOrderTable.amount;

    while (orderDetailsTable.hasNext()) {
        var rs = orderDetailsTable.getNext();
        recordCount = recordCount + 1;
    }
    return recordCount;
}

function testSelectWithJoinAndWhereWithGroupByWithLimit() returns (int) {

    table<Person> personTable = table{};
    table<Order> orderTable = table{};

    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    Order o1 = { personId: 1, orderId: 1234, items: "pen, book, eraser", amount: 34.75 };
    Order o2 = { personId: 1, orderId: 2314, items: "dhal, rice, carrot", amount: 14.75 };
    Order o3 = { personId: 2, orderId: 5643, items: "Macbook Pro", amount: 2334.75 };
    Order o4 = { personId: 3, orderId: 8765, items: "Tshirt", amount: 20.75 };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    _ = orderTable.add(o1);
    _ = orderTable.add(o2);
    _ = orderTable.add(o3);
    _ = orderTable.add(o4);

    table<OrderDetails> orderDetailsTable = from personTable as tempPersonTable join orderTable as tempOrderTable on
    tempPersonTable.id == tempOrderTable.personId
    select tempOrderTable.orderId as orderId, tempPersonTable.name as personName, tempOrderTable.items as
    items, tempOrderTable.amount as amount group by tempOrderTable.orderId, tempPersonTable.name,
    tempOrderTable.items, tempOrderTable.amount limit 2;

    while (orderDetailsTable.hasNext()) {
        var rs = orderDetailsTable.getNext();
        recordCount = recordCount + 1;
    }
    return recordCount;
}

function testTableToString() returns table<record {}> {
    table<Person> personTable = table{};
    int recordCount = 0;
    Person p1 = { id: 1, age: 25, salary: 300.50, name: "jane", married: true };
    Person p2 = { id: 2, age: 26, salary: 400.50, name: "kane", married: false };
    Person p3 = { id: 3, age: 27, salary: 500.50, name: "jack", married: true };
    Person p4 = { id: 4, age: 28, salary: 600.50, name: "alex", married: false };

    _ = personTable.add(p1);
    _ = personTable.add(p2);
    _ = personTable.add(p3);
    _ = personTable.add(p4);

    table<Person> personTableCopy = from personTable select *;

    return personTableCopy;
}
