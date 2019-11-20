### SQL Injection pattern detection in NodeJS + TypeORM ###

Plugin for WEBSTORM IDE

This plugin highlights suspicious code that may lead to SQL injection when using TypeORM.
See: https://www.owasp.org/index.php/SQL_Injection

#### Typeorm Connection.query(...) samples:
###### no Problem highlighted:
``` javascript
    public function noProblemFound()
    {
        // constant value
        const aValue = 10;
        // Templated string
        this.connection.query(`SELECT XYZ, ABC FROM A WHERE ABC = ${aValue}`);
        // Concatenated string (bad idea anyway)
        this.connection.query('SELECT XYZ, ABC FROM A WHERE ABC = ' + aValue);

        // constant array values
        const tokens = ['A VALUE', 'ANOTHER VALUE', variable]
        this.connection.query('SELECT XYZ, ABC FROM A WHERE ABC = ' + tokens[0]);
        this.connection.query('SELECT XYZ, ABC FROM A WHERE ABC = ' + tokens[1]);
    }
```

###### Problem found:
``` javascript
    public function suspiciousCodeFound(externalVariable)
    {
        // non constant value
        let aValue = 10;
        this.connection.query(`SELECT XYZ, ABC FROM A WHERE ABC = ${aValue}`);
        this.connection.query('SELECT XYZ, ABC FROM A WHERE ABC = ' + aValue);
        this.connection.query(`SELECT XYZ, ABC FROM A WHERE ABC = ${externalVariable}`);
        this.connection.query(`SELECT XYZ, ABC FROM A WHERE ABC = ${this.getSomething()}`);

        const tokens = ['A VALUE', 'ANOTHER VALUE', externalVariable]
        this.connection.query('SELECT XYZ, ABC FROM A WHERE ABC = ' + tokens[2]);

        // ==> PREFERE THAT:
        this.connection.query(`SELECT XYZ, ABC FROM A WHERE ABC = $1`, [aValue]);

    }
```

#### Typeorm SelectQueryBuilder(...) samples:

- innerJoin conditions
- leftJoin conditions
- innerJoinAndSelect conditions
- leftJoinAndSelect conditions
- innerJoinAndMapMany conditions
- innerJoinAndMapOne conditions
- leftJoinAndMapMany conditions
- leftJoinAndMapOne conditions
- join conditions
- where
- andWhere
- orWhere

###### Problem found:
``` javascript
    public function suspiciousCodeFound()
    {
        let externalValue = ... // non constant values (same as above)
        return this.projectRepository.manager
            .createQueryBuilder(Project, 'project')
            .leftJoinAndSelect('project.subProjects', 'subProjects', externalValue)
            .where('project.isVisible = true')
            .getMany();
    }
```
