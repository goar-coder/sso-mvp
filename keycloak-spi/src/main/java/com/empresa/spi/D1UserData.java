package com.empresa.spi;

import java.util.List;

public class D1UserData {
    private String id;
    private String username;
    private String email;
    private String firstName;
    private String lastName;
    private boolean active;
    private List<String> appRoles;

    public String getId()                  { return id; }
    public void setId(String id)           { this.id = id; }

    public String getUsername()            { return username; }
    public void setUsername(String u)      { this.username = u; }

    public String getEmail()               { return email; }
    public void setEmail(String e)         { this.email = e; }

    public String getFirstName()           { return firstName; }
    public void setFirstName(String f)     { this.firstName = f; }

    public String getLastName()            { return lastName; }
    public void setLastName(String l)      { this.lastName = l; }

    public boolean isActive()              { return active; }
    public void setActive(boolean a)       { this.active = a; }

    public List<String> getAppRoles()      { return appRoles; }
    public void setAppRoles(List<String> r){ this.appRoles = r; }
}
